// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.jetbrains.annotations.ApiStatus

// Progress a program reports out-of-band via OSC 9;4. Part of the backend-agnostic API; see
// TerminalEmulator.kt.

/** How a program wants its `OSC 9;4` progress rendered. See [TerminalProgress]. */
@ApiStatus.Internal
enum class TerminalProgressState {
  /** `OSC 9;4;1` — an ordinary determinate progress bar. */
  NORMAL,

  /** `OSC 9;4;2` — the operation failed; render the bar as an error. */
  ERROR,

  /** `OSC 9;4;3` — the operation is running, but how far along it is is unknown. */
  INDETERMINATE,

  /** `OSC 9;4;4` — the operation is paused or needs the user's attention. */
  PAUSED,
}

/**
 * Progress a program reports via `OSC 9;4;<state>[;<percent>]` — the ConEmu progress extension, emitted
 * by (among others) PowerShell, `winget` and `cargo` to drive a taskbar / tab progress indicator. See
 * [TerminalEmulator.progress]; the absence of a report (including the `OSC 9;4;0` "remove" form) is
 * modeled as a null [TerminalEmulator.progress] rather than a state of its own.
 */
@ApiStatus.Internal
data class TerminalProgress(
  val state: TerminalProgressState,
  /**
   * Completion percentage in `0..100`, or null when the program reported no usable value: an
   * [TerminalProgressState.INDETERMINATE] report (which never carries one), a state that allows the
   * percentage to be omitted ([TerminalProgressState.ERROR] / [TerminalProgressState.PAUSED]), or an
   * unparseable one. A value above 100 is clamped to 100; a [TerminalProgressState.NORMAL] report with
   * no percentage at all means `0`.
   */
  val percent: Int? = null,
) {
  init {
    require(percent == null || percent in 0..100) { "percent must be null or in 0..100, was $percent" }
  }
}
