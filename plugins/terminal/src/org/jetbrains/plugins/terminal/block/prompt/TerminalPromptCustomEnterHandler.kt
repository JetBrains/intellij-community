// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.prompt

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

/**
 * Allows handling Enter keystroke in the Terminal prompt in your own way.
 * By default, Enter sends the typed command to the Shell.
 */
@ApiStatus.Internal
interface TerminalPromptCustomEnterHandler {
  /**
   * Called when user is pressing Enter keystroke in the Terminal prompt.
   * @return whether enter action is consumed. If true is returned, all other handlers will be skipped, including the default one.
   */
  @RequiresEdt
  fun handleEnter(model: TerminalPromptModel): Boolean

  companion object {
    internal val EP_NAME: ExtensionPointName<TerminalPromptCustomEnterHandler> = ExtensionPointName.create("org.jetbrains.plugins.terminal.promptCustomEnterHandler")
  }
}
