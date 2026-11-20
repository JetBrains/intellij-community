# KetraTerm in the Reworked Terminal

## Run

1. Enable the Bazel plugin in the source IDE and open the repository’s `.bazelproject` with **File | Open**.
2. Choose **Run | Run…** and select **Run //build:idea_community**.
3. In the IDE that opens, use **Find Action** to open **Registry**. Set `terminal.emulator` to `KetraTerm`.
4. Open a new Reworked Terminal tab to use KetraTerm.

## How it works

IntelliJ manages the process, tool window, tabs, and splits.
KetraTerm parses terminal output, handles input, and draws the terminal with Swing.
Local sessions use KetraTerm shell hooks for command tracking and completion. Remote sessions use IntelliJ shell hooks.

The native view supports selection, clipboard actions, links, literal search, and local completion.
Regex search, IntelliJ editor inline completion, and session transfer are unavailable in this view.

See the [dependencies and tests](../../libraries/ketraterm/README.md).

## Benchmark highlights

These are medians from nine samples on Windows with JDK 25.0.2 and KetraTerm 1.0.1. KetraTerm used its native Swing view.
In each log row, the first value measures through the paint that shows the marker. The second measures until both the paint and full history are ready.
Allocation is cumulative across JVM threads.

| Workload and measurement | JediTerm | Ghostty | KetraTerm |
|---|---:|---:|---:|
| JVM allocation from session start to first painted marker (MiB) | **2.30** | 2.69 | 4.87 |
| Read a 1 MiB ASCII log (ms) | 54.93 / 54.93 | **18.26** / 350.11 | 20.36 / **20.36** |
| JVM allocation for the 1 MiB log (MiB) | 60.97 / 60.97 | 4.93 / 371.32 | **0.44 / 0.44** |
| Read a 5 MiB ASCII log (ms) | 164.68 / 164.68 | **43.81** / 2,101.25 | 56.22 / **56.22** |
| JVM allocation for the 5 MiB log (MiB) | 307.38 / 307.38 | 17.89 / 1,999.06 | **6.68 / 6.68** |
| Move the cursor left one cell (ms until ready) | 20.22 | 21.06 | **0.38** |
| Echo one character (ms until painted) | 1.89 | 21.23 | **0.84** |
| Move to the next file-picker row (ms until painted) | 21.52 | 22.03 | **0.60** |
| Repaint a progress bar (ms of paint per update) | 0.89 | 0.89 | **0.49** |
| Paint after 10,000 cursor writes in a reused session (ms) | 21.80 | 21.41 | **17.52** |
| Paint bidirectional text (ms) | 117.44 | **15.41** | 35.93 |
| Search 26,000 lines and paint the match (ms) | **34.52** | 39.63 | 104.58 |
