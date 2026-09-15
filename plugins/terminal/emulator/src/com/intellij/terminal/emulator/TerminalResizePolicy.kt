// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.jetbrains.annotations.ApiStatus

// Resize policy: how a resize that grows the screen row count treats existing scrollback. Part of the
// backend-agnostic API; see TerminalEmulator.kt.

/**
 * Policy for a [TerminalEmulator.resize] that grows the row count of the active screen.
 *
 * Growing the row count can either pull rows back from scrollback onto the screen or add new
 * blank rows at the bottom and leave scrollback as is. The policy decides which. It has no effect
 * when the row count shrinks.
 */
@ApiStatus.Internal
enum class ScrollbackPullPolicy {
  /** Pull rows back from scrollback, no matter where the cursor sits. */
  ALWAYS,

  /**
   * Pull rows back from scrollback only when the cursor sits on the bottom row.
   * This is the default.
   */
  CURSOR_AT_BOTTOM,

  /**
   * Never pull rows back from scrollback. A grown screen gets new blank rows at the bottom, and
   * the cursor keeps its row.
   *
   * Pick this when the PTY keeps its own screen buffer with little or no scrollback of its own,
   * so the two buffers would otherwise disagree by the number of rows the resize adds.
   * Windows ConPTY is the motivating case.
   */
  NEVER,
}
