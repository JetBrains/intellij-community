// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import java.util.*

class CommandHistoryManager(session: TerminalSession) {
  private val mutableHistory: MutableSet<String> = Collections.synchronizedSet(LinkedHashSet())

  /**
   * History in a chronological order, but with removed repetitions.
   * The latest commands are positioned at the end of the list.
   */
  val history: List<String>
    get() = mutableHistory.toList()

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

  private fun initCommandHistory(history: String) {
    if (mutableHistory.isNotEmpty()) {
      return
    }
    val unsortedHistory = history.split("\n").mapNotNull { row ->
      // row is in the format <spaces><row_number><spaces><command><spaces>
      // retrieve command from the row
      row.trimStart().trimStart { Character.isDigit(it) }.trim().takeIf { it.isNotEmpty() }
    }
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