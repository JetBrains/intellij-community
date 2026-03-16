// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.history

import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptModel
import org.jetbrains.plugins.terminal.block.session.BlockTerminalSession
import org.jetbrains.plugins.terminal.block.session.ShellCommandListener
import org.jetbrains.plugins.terminal.exp.completion.TerminalShellSupport
import java.util.Collections

internal class CommandHistoryManager(
  private val session: BlockTerminalSession,
  private val promptModel: TerminalPromptModel
) {
  private val mutableHistory: MutableSet<String> = Collections.synchronizedSet(LinkedHashSet())

  init {
    session.addCommandListener(object : ShellCommandListener {
      override fun commandHistoryReceived(history: String) {
        initCommandHistory(history)
      }

      override fun commandStarted(command: String) {
        val trimmedCommand = command.trim()
        if (trimmedCommand.isNotEmpty()) {
          // move the command to the end to make it shown first
          mutableHistory.remove(trimmedCommand)
          mutableHistory.add(trimmedCommand)
        }
      }
    })
  }

  /**
   * Iterates the custom [TerminalCommandHistoryProvider]'s and returns the first found not null history.
   * If no providers overridden the history, the default shell command history is returned.
   *
   * @return history in a chronological order, but with removed repetitions.
   * The latest commands are positioned at the end of the list.
   */
  fun getHistory(): List<String> {
    val historyFromProvider = TerminalCommandHistoryProvider.EP_NAME.extensionList.firstNotNullOfOrNull { provider ->
      provider.getCommandHistory(promptModel)
    }
    return historyFromProvider ?: mutableHistory.toList()
  }

  private fun initCommandHistory(history: String) {
    if (mutableHistory.isNotEmpty()) {
      return
    }
    val shellSupport = TerminalShellSupport.findByShellType(session.shellIntegration.shellType) ?: return
    val unsortedHistory = shellSupport.parseCommandHistory(history)
    // filter repeating items preserving the order
    // start from the end, because the latest commands have a prioritized position
    val historySet = LinkedHashSet<String>()
    for (ind in (unsortedHistory.size - 1) downTo 0) {
      historySet.add(unsortedHistory[ind])
    }
    // reverse the history to place the most recent commands at the end of the set
    val reversedHistory = historySet.reversed()
    mutableHistory.addAll(reversedHistory)
  }
}
