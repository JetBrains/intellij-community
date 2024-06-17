// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.history

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptModel

/**
 * Allows replacing default shell command history with some custom history.
 * The history is requested from all providers in a row. The first not null history will be effective.
 * All other providers will be skipped.
 * If all providers returned null, then the default shell command history will be shown.
 */
@ApiStatus.Internal
interface TerminalCommandHistoryProvider {
  /**
   * Return not the null list from this method if you need to replace the default shell command history with your own history.
   * The most recent history items should be at the end of the list.
   */
  fun getCommandHistory(promptModel: TerminalPromptModel): List<String>?

  companion object {
    internal val EP_NAME: ExtensionPointName<TerminalCommandHistoryProvider> = ExtensionPointName.create("org.jetbrains.plugins.terminal.commandHistoryProvider")
  }
}
