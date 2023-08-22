// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import java.util.concurrent.TimeUnit

class BashCompletionManager(session: TerminalSession) : BaseShCompletionManager(session) {
  override fun doResetPrompt(disposable: Disposable) {
    val model = session.model
    val promptRestoredFuture = checkTerminalContent(disposable) {
      model.getScreenText().isEmpty()
    }

    // \u000c - send Ctrl + L to insert new lines, so current command will be on top of the screen
    // \u0015 - send Ctrl + U to clear the lines with typed command
    val resetCommand = "\u000c\u0015"
    session.terminalStarter.sendString(resetCommand, false)

    promptRestoredFuture.get(3000, TimeUnit.MILLISECONDS)
    model.clearHistory()
  }
}