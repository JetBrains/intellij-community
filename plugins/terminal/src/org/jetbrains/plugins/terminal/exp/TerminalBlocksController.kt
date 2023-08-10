// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.jediterm.core.util.TermSize
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent

class TerminalBlocksController(
  project: Project,
  private val session: TerminalSession,
  settings: JBTerminalSystemSettingsProviderBase
) : TerminalContentController, TerminalCommandExecutor, ShellCommandListener {
  private val blocksComponent: TerminalBlocksComponent

  init {
    blocksComponent = TerminalBlocksComponent(project, session, settings, commandExecutor = this, parentDisposable = this)
    // Show initial terminal output (prior to the first prompt) in a separate block.
    // `initialized` event will finish the block.
    blocksComponent.installRunningPanel()
    blocksComponent.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        val newSize = getTerminalSize() ?: return
        session.postResize(newSize)
      }
    })

    session.addCommandListener(this)
    session.model.addTerminalListener(object : TerminalModel.TerminalListener {
      override fun onAlternateBufferChanged(enabled: Boolean) {
        invokeLater {
          blocksComponent.toggleFullScreen(enabled)
        }
      }
    })
  }

  override fun startCommandExecution(command: String) {
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
        blocksComponent.installRunningPanel()
        session.executeCommand(command)
      }
    }
  }

  override fun initialized() {
    finishCommandBlock(true)
  }

  override fun commandStarted(command: String) {
    session.model.isCommandRunning = true
  }

  override fun commandFinished(command: String, exitCode: Int, duration: Long) {
    finishCommandBlock(false)
  }

  private fun finishCommandBlock(removeIfEmpty: Boolean) {
    blocksComponent.makeCurrentBlockReadOnly(removeIfEmpty)

    val model = session.model
    model.isCommandRunning = false

    // prepare terminal for the next command
    model.withContentLock {
      model.clearAllExceptPrompt()
    }

    model.commandExecutionSemaphore.up()

    invokeLater {
      blocksComponent.resetPromptPanel()
    }
  }

  override fun getTerminalSize(): TermSize? = blocksComponent.getTerminalSize()

  override fun isFocused(): Boolean {
    return blocksComponent.isFocused()
  }

  override fun dispose() {
  }

  override fun getComponent(): JComponent = blocksComponent

  override fun getPreferredFocusableComponent(): JComponent = blocksComponent.getPreferredFocusableComponent()
}