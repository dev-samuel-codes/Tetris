param(
    [ValidatePattern('^[a-zA-Z0-9_-]+$')]
    [string]$Label = 'before'
)
$ErrorActionPreference = 'Stop'
function Invoke-AnalysisJava([string[]]$JavaArgs, [string]$LogPath) {
    # Windows PowerShell 5 treats native stderr warnings as ErrorRecords.
    $ErrorActionPreference = 'Continue'
    & java @JavaArgs > $LogPath 2>&1
    $javaExit = $LASTEXITCODE
    if ($javaExit -ne 0) { throw "Java exited with code $javaExit. See $LogPath" }
}
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    $spotJar = Join-Path $projectRoot '.analysis-tools/spotbugs-4.10.5/lib/spotbugs.jar'
    $ckJar = Join-Path $projectRoot '.analysis-tools/ck-0.7.0-jar-with-dependencies.jar'
    if (!(Test-Path $spotJar) -or !(Test-Path $ckJar)) {
        throw 'Run scripts/setup-analysis.ps1 first.'
    }
    $reportDir = Join-Path $projectRoot "analysis/reports/$Label"
    if (Test-Path $reportDir) { throw "Report already exists: $reportDir. Use a different -Label to preserve evidence." }
    New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
    # A fresh output directory prevents stale class files contaminating analysis.
    $classes = Join-Path $projectRoot ("out/analysis-" + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force -Path $classes | Out-Null
    $sources = @(Get-ChildItem src -Recurse -Filter '*.java' | ForEach-Object FullName)
    & javac --release 17 -encoding UTF-8 -g -d $classes $sources 2> "$reportDir/build.log"
    if ($LASTEXITCODE -ne 0) { throw 'Compilation failed. See build.log.' }
    Invoke-AnalysisJava -JavaArgs @('-jar', $ckJar, (Join-Path $projectRoot 'src'), 'false', '0', 'false', "$reportDir/") -LogPath "$reportDir/ck.log"
    Invoke-AnalysisJava -JavaArgs @('-jar', $spotJar, '-textui', '-effort:max', '-low', '-xml:withMessages', '-sourcepath', 'src', '-output', "$reportDir/spotbugs.xml", $classes) -LogPath "$reportDir/spotbugs.log"
    [xml]$bugs = Get-Content -Raw -Encoding UTF8 "$reportDir/spotbugs.xml"
    if ([int]$bugs.BugCollection.Errors.errors -gt 0 -or [int]$bugs.BugCollection.Errors.missingClasses -gt 0) {
        throw 'SpotBugs reported analysis errors or missing classes; inspect spotbugs.xml.'
    }
    $findings = @($bugs.BugCollection.BugInstance | Where-Object { $_ } | ForEach-Object {
        $line = @($_.SourceLine)[0]
        if (!$line) { $line = @($_.Method.SourceLine)[0] }
        if (!$line) { $line = @($_.Class.SourceLine)[0] }
        [pscustomobject]@{
            priority = $_.priority
            rank = $_.rank
            type = $_.type
            class = $_.Class.classname
            file = $line.sourcepath
            line = $line.start
            message = $_.LongMessage
        }
    })
    $findings | Export-Csv "$reportDir/spotbugs-findings.csv" -NoTypeInformation -Encoding UTF8
    $metrics = @(Import-Csv "$reportDir/class.csv")
    if ($metrics.Count -eq 0) { throw 'CK produced no class metrics.' }
    $metrics | Select-Object class,type,wmc,cbo,rfc,lcom,loc,dit | Export-Csv "$reportDir/selected-metrics.csv" -NoTypeInformation -Encoding UTF8
    Invoke-AnalysisJava -JavaArgs @('-version') -LogPath "$reportDir/java-version.log"
    $manifest = [ordered]@{
        commit = (git rev-parse HEAD)
        workingTree = @(git status --short)
        measuredAt = [DateTimeOffset]::Now.ToString('o')
        sourceScope = 'src/**/*.java only; tests excluded'
        sourceFiles = $sources.Count
        classRows = $metrics.Count
        java = @(Get-Content "$reportDir/java-version.log" | ForEach-Object { [string]$_ })
        compileRelease = 17
        spotbugs = '4.10.5; effort=max; threshold=low (all priorities)'
        ck = '0.7.0; useJars=false; partition=0; variablesAndFields=false'
        highPriorityFindings = @($findings | Where-Object priority -eq '1').Count
        totalFindings = $findings.Count
        sourceHashes = @($sources | ForEach-Object {
            [ordered]@{ path = $_.Substring($projectRoot.Length + 1); sha256 = (Get-FileHash $_ -Algorithm SHA256).Hash }
        })
    }
    $manifest | ConvertTo-Json -Depth 5 | Set-Content "$reportDir/manifest.json" -Encoding UTF8
    Write-Output "Analysis complete: $reportDir"
    Write-Output "Classes: $($metrics.Count); findings: $($findings.Count); high priority: $($manifest.highPriorityFindings)"
} finally {
    Pop-Location
}
