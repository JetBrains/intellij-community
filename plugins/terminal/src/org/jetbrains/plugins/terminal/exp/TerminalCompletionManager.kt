// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.jediterm.terminal.TerminalOutputStream

class TerminalCompletionManager(private val model: TerminalModel,
                                private val terminalOutputGetter: () -> TerminalOutputStream) {
  fun invokeCompletion(command: String) {
    terminalOutputGetter().sendString(command + "\t", false)
  }

  fun resetPrompt(promptLines: Int, newPromptShown: Boolean) {
    if (newPromptShown) {
      model.withLock {
        model.clearAllExceptPrompt(promptLines)
      }
    }

    // Send Ctrl+U to clear the line with typings
    // There are two invocations: first will clear possible "zsh: do you wish to see all..." question,
    // and the second will clear the typed command
    val command = "\u0015\u0015"
    terminalOutputGetter().sendString(command, false)
  }
}