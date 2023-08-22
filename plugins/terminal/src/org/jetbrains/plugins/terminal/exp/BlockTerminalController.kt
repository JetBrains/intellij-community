// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.thisLogger
import com.jediterm.core.util.TermSize

class BlockTerminalController(
  private val session: TerminalSession,
  private val outputController: TerminalOutputController,
  private val promptController: TerminalPromptController
) : ShellCommandListener {
  init {
    session.addCommandListener(this)
    // Show initial terminal output (prior to the first prompt) in a separate block.
    // `initialized` event will finish the block.
    outputController.startCommandBlock(null)
    outputController.isFocused = true
    promptController.promptIsVisible = false
    session.model.isCommandRunning = true
  }

  fun resize(newSize: TermSize) {
    session.postResize(newSize)
  }

  fun startCommandExecution(command: String) {
    ApplicationManager.getApplication().executeOnPooledThread {
      val model = session.model
      if (model.commandExecutionSemaphore.waitFor(3000)) {
        model.commandExecutionSemaphore.down()
      }
      else {
        thisLogger().error("Failed to acquire the command execution lock to execute command: '$command'\n" +
                           "Text buffer:\n" + model.withContentLock { model.getAllText() })
      }

      invokeLater {
        outputController.startCommandBlock(command)
        promptController.promptIsVisible = false
        session.executeCommand(command)
      }
    }
  }

  override fun commandStarted(command: String) {
    session.model.isCommandRunning = true
  }

  override fun initialized() {
    finishCommandBlock()
  }

  override fun commandFinished(command: String, exitCode: Int, duration: Long) {
    finishCommandBlock()
  }

  private fun finishCommandBlock() {
    outputController.finishCommandBlock()

    val model = session.model
    model.isCommandRunning = false

    // prepare terminal for the next command
    model.withContentLock {
      model.clearAllExceptPrompt()
    }

    model.commandExecutionSemaphore.up()

    invokeLater {
      promptController.reset()
      promptController.promptIsVisible = true
    }
  }
}