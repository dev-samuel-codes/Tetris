# VS Code analysis workflow

Open `Tetris-analysis.code-workspace` in VS Code and use **Terminal > Run Task**
to select `Analysis: install tools` or `Analysis: measure and inspect`.
The latter asks for a new report label. The initial completed report is `baseline`.
The `before` folder contains an incomplete first run interrupted by a Windows
PowerShell native-stderr warning; do not use it for comparison.

Run from the project folder containing `src` and `scripts`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/setup-analysis.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/analyze.ps1 -Label before
# After refactoring:
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/analyze.ps1 -Label after
```

The scripts require a JDK 17 or newer. They do not add runtime dependencies to the game.
Downloaded binaries are stored in `.analysis-tools/` and excluded from Git.
Existing report labels cannot be overwritten. Use a new label for another run.

## Tools and scope

- SpotBugs 4.10.5: https://spotbugs.readthedocs.io/en/stable/running.html
- CK 0.7.0: https://github.com/mauricioaniche/ck
- Only production Java sources under `src` are measured; tests and Python AI runtime are excluded.
- Classes are compiled with `--release 17` into a fresh directory before bytecode analysis.
- SpotBugs runs with maximum effort and all priorities enabled. Priority 1 means High, 2 Normal, 3 Low. Priority and bug rank are different fields. Use High as the initial triage group, and also review bug ranks; the assignment does not define an exact severity mapping.
- `manifest.json` records scope, commit, tool settings, source hashes, and counts. A changed manifest working tree is expected when adding this analysis setup.

## Metrics and interpretation

`class.csv` and `method.csv` preserve CK output. `selected-metrics.csv` selects six class-level metrics:

| Column | Meaning |
| --- | --- |
| wmc | CK's aggregate method complexity / McCabe branch metric |
| cbo | Coupling between objects |
| rfc | Response for a class, using CK's invocation-count definition |
| lcom | CK's original lack-of-cohesion metric |
| loc | Source lines under CK's AST-based counting rules |
| dit | Inheritance depth |

The method-level `wmc` column provides finer complexity evidence.
CK distinguishes named, inner, and anonymous classes. Keep that scope consistent in both runs.
Do not sum class LOC as physical project LOC: nested class source ranges can overlap.
LCOM has known interpretation limitations. Do not assume every metric must decrease.
Use the same tool versions, settings, and aggregation rules for before/after comparison.

The course deck names Metrics Reloaded for metric measurement. CK is an alternative tool,
not Metrics Reloaded, and its numeric definitions may differ. State this substitution in
the submission and confirm course acceptance before relying on CK alone for grading.

## Reports

- `spotbugs.xml`: detailed original bug report with messages.
- `spotbugs-findings.csv`: sortable problem list with priority, rank, source location and message.
- `selected-metrics.csv`: selected metrics for comparison.
- `class.csv`, `method.csv`: original CK metric data.
- `*.log`: build and analyzer diagnostics.

These results identify candidates for review. No finding is automatically fixed or suppressed.
