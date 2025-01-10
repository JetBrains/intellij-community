// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.util.concurrency.annotations.RequiresEdt

/**
 * Should manage the vertical scroll offset of the terminal output.
 */
internal interface TerminalOutputScrollingModel {
  /**
   * Scroll to the cursor forcefully. Should be used only in case of explicit user action.
   */
  @RequiresEdt
  fun scrollToCursor()
}