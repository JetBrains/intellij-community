// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

/**
 * Should manage the vertical scroll offset of the terminal output.
 */
@ApiStatus.Internal
interface TerminalOutputScrollingModel {
  /**
   * If [force] is true, the vertical scroll offset will be unconditionally adjusted to make cursor visible on the screen.
   * [force] option should be used only in response to explicit user action.
   *
   * If [force] is false, the vertical scroll offset will be changed only if user now is following the screen end.
   * If a user's scroll position is somewhere in the history, the scroll request will be ignored.
   */
  @RequiresEdt
  fun scrollToCursor(force: Boolean)

  /**
   * Scrolls the output by the given number of whole grid lines, keeping the resting position aligned to a line boundary.
   * Negative [lines] scrolls up (towards the history), positive scrolls down (towards the latest output).
   *
   * This unsticks the scroll from the bottom, unless the resulting position reaches the bottom again,
   * in which case the following of the new output is resumed.
   */
  @RequiresEdt
  fun scrollByLines(lines: Int)

  /**
   * Scrolls the output by the given number of pages (a page is the number of whole lines that fit into the viewport).
   * Negative [pages] scrolls up, positive scrolls down. See [scrollByLines] for the stick/unstick behavior.
   */
  @RequiresEdt
  fun scrollByPages(pages: Int)

  companion object {
    val KEY: Key<TerminalOutputScrollingModel> = Key.create("TerminalOutputScrollingModel")
  }
}