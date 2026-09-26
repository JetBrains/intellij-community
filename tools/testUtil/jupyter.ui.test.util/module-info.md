### Module `intellij.jupyter.ui.test.util`

> **Warning:** The API of this module is unstable. Use it at your own risk — it may change without notice between releases.

Provides shared UI test utilities for Jupyter notebook testing, used by both Python and Kotlin Notebook test suites
to avoid duplication. The module is organized into the following packages:

- **`codeinsight`** — helpers for code analysis results, highlighting verification, and look-and-feel checks.
- **`completion`** — utilities for testing code completion variants and shared completion test scenarios.
- **`kernel`** — helpers for kernel lifecycle management, running cells, and waiting for execution to finish.
- **`project`** — utilities for interacting with the welcome screen and project setup.
- **`tables`** — helpers for inspecting and filtering Jupyter output tables.
- **`utils`** — general-purpose utilities: cell info accessors, debug mode helpers, Jupyter console and variables panel accessors,
project structure utilities, registry and settings helpers, wait/polling utilities, and shared smoke test checks.
