// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.jetbrains.annotations.ApiStatus

// Push callbacks, both fired synchronously inside TerminalEmulator.write. Everything else about the
// terminal is polled state. Part of the backend-agnostic API; see TerminalEmulator.kt.

/**
 * Events emitted while processing input. Callbacks fire synchronously inside
 * [TerminalEmulator.write]; they must not re-enter the emulator. All methods default to no-ops.
 *
 * Only [onRespondToHost] is guaranteed to be delivered by every backend. [onBell] and
 * [onTitleChanged] are optional and backend-dependent: a backend that does not surface them simply
 * never calls them, so consumers must not depend on them for correctness. In particular, the bundled
 * Ghostty backend delivers [onRespondToHost] and [onBell]; the window title is instead available by
 * polling [TerminalEmulator.title].
 */
@ApiStatus.Internal
interface TerminalListener {
  /** Bytes the terminal wants sent back to the host/PTY (query responses such as DSR / DA). */
  fun onRespondToHost(data: ByteArray) {}

  /** The program rang the bell (BEL, 0x07). Delivered by the Ghostty backend. */
  fun onBell() {}

  /** The window/tab title changed (OSC 0/2). Optional; backend-dependent (not delivered by the Ghostty backend). */
  fun onTitleChanged(title: String) {}
}

/**
 * Receives application-specific *custom* OSC commands — `ESC]1341;arg1;arg2…` closed by BEL or ST.
 *
 * OSC 1341 is the JetBrains shell-integration command number. Delivered synchronously inside
 * [TerminalEmulator.write]; the callback must not re-enter the emulator.
 */
@ApiStatus.Internal
fun interface TerminalCustomCommandListener {
  /** Handles one custom command; [args] are the arguments after the `1341` number. */
  fun onCustomCommand(args: List<String>)
}
