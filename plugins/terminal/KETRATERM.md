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
To enable the experimental IntelliJ document model and command-block APIs, set the VM option `-Dketraterm.experimental.enableDocumentModel=true`.

See the [dependencies and tests](../../libraries/ketraterm/README.md).
