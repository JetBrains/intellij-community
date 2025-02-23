// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend

import com.intellij.util.concurrency.annotations.RequiresEdt

/**
 * Should manage the vertical scroll offset of the terminal output.
 */
internal interface TerminalOutputScrollingModel {
  /**
   * If [force] is true, the vertical scroll offset will be unconditionally adjusted to make cursor visible on the screen.
   * [force] option should be used only in response to explicit user action.
   *
   * If [force] is false, the vertical scroll offset will be changed only if user now is following the screen end.
   * If a user's scroll position is somewhere in the history, the scroll request will be ignored.
   */
  @RequiresEdt
  fun scrollToCursor(force: Boolean)
}