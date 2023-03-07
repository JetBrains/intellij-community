// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.jediterm.core.util.Ascii
import com.jediterm.terminal.TerminalOutputStream

class TerminalCompletionManager(private val model: TerminalModel,
                                private val terminalOutputGetter: () -> TerminalOutputStream) {
  fun invokeCompletion(command: String) {
    terminalOutputGetter().sendString(command + "\t", false)
  }

  fun resetPrompt(typedLength: Int, promptLines: Int, newPromptShown: Boolean) {
    model.withLock {
      if (newPromptShown) {
        model.clearAllExceptPrompt(promptLines)
      }
      else model.clearLines(promptLines, model.screenLinesCount - promptLines)
    }

    val backSpaceCommands = ByteArray(typedLength + 100) { Ascii.DEL }
    terminalOutputGetter().sendBytes(backSpaceCommands, false)
  }
}