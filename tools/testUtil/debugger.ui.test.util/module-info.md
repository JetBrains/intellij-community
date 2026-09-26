### Module `intellij.debugger.ui.test.util`

> **Warning:** The API of this module is unstable. Use it at your own risk — it may change without notice between releases.

Provides shared UI test utilities for debugger interactions.
The module exposes `PlatformDebugSteps` (accessible via the `IdeaFrameUI.debugger` extension property),
which offers high-level helpers for automated debugger UI testing:
setting and removing breakpoints, stepping through code, inspecting the stack frames and variables tree,
evaluating expressions, and waiting for the Debug tool window to become ready.

Used by UI tests across multiple IDE products, including CLion, PyCharm, WebStorm, RustRover, Rider, and Kotlin Notebooks.
