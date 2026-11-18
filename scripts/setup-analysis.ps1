$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$toolDir = Join-Path $projectRoot '.analysis-tools'
New-Item -ItemType Directory -Force -Path $toolDir | Out-Null
$spotVersion = '4.10.5'
$spotArchive = Join-Path $toolDir "spotbugs-$spotVersion.zip"
$ckJar = Join-Path $toolDir 'ck-0.7.0-jar-with-dependencies.jar'
if (!(Test-Path (Join-Path $toolDir "spotbugs-$spotVersion/lib/spotbugs.jar"))) {
    Invoke-WebRequest -UseBasicParsing -Uri "https://github.com/spotbugs/spotbugs/releases/download/$spotVersion/spotbugs-$spotVersion.zip" -OutFile $spotArchive
    if ((Get-FileHash $spotArchive -Algorithm SHA256).Hash.ToLowerInvariant() -ne 'f8eb9cf4ac8e1b29b3e29567541eef3f30b3bf49ff3880670a7963092cd8a859') {
        throw 'SpotBugs checksum mismatch'
    }
    Expand-Archive -LiteralPath $spotArchive -DestinationPath $toolDir -Force
}
if (!(Test-Path $ckJar)) {
    $ckUrl = 'https://repo.maven.apache.org/maven2/com/github/mauricioaniche/ck/0.7.0/ck-0.7.0-jar-with-dependencies.jar'
    Invoke-WebRequest -UseBasicParsing -Uri $ckUrl -OutFile $ckJar
    $expected = (Invoke-WebRequest -UseBasicParsing -Uri "$ckUrl.sha1").Content.Trim()
    if ((Get-FileHash $ckJar -Algorithm SHA1).Hash.ToLowerInvariant() -ne $expected.ToLowerInvariant()) {
        throw 'CK checksum mismatch'
    }
}
Write-Output 'Installed SpotBugs 4.10.5 and CK 0.7.0'
