// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

class CommandHistoryManager(session: TerminalSession) {
  private val mutableHistory: MutableList<String> = mutableListOf()
  val history: List<String>
    get() = mutableHistory

  init {
    session.addCommandListener(object : ShellCommandListener {
      override fun commandHistoryReceived(history: String) {
        initCommandHistory(history)
      }

      override fun commandStarted(command: String) {
        mutableHistory.add(command)
      }
    })
  }

  private fun initCommandHistory(history: String) {
    if (mutableHistory.isNotEmpty()) {
      return
    }
    history.split("\n").forEach { row ->
      // row is in the format <spaces><row_number><spaces><command>
      // retrieve command from the row
      val command = row.trimStart().trimStart { Character.isDigit(it) }.trimStart()
      if (command.isNotBlank() && mutableHistory.lastOrNull() != command) {
        mutableHistory.add(command)
      }
    }
  }
}