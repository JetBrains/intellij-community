// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.prompt

import com.intellij.openapi.editor.ex.EditorEx
import org.jetbrains.plugins.terminal.exp.BlockTerminalSession
import org.jetbrains.plugins.terminal.exp.CommandFinishedEvent
import org.jetbrains.plugins.terminal.exp.ShellCommandExecutionManager.KeyBinding
import org.jetbrains.plugins.terminal.exp.ShellCommandListener
import org.jetbrains.plugins.terminal.exp.invokeLater
import org.jetbrains.plugins.terminal.util.ShellType
import java.awt.event.KeyEvent

/**
 * When the terminal is busy, user can continue typing (i.e., next command).
 * Supports extracting this collected input buffer and inserting it to the prompt editor when the command is finished.
 * Without this class, the collected input buffer will be lost and the user would have to type command again.
 */
internal class ShellEditorBufferReportShellCommandListener(
  private val blockTerminalSession: BlockTerminalSession,
  private val model: TerminalPromptModel,
  private val editor: EditorEx
) : ShellCommandListener {

  override fun commandBufferReceived(buffer: String) {
    invokeLater {
        model.commandText = buffer
        editor.caretModel.moveToOffset(editor.document.textLength)
    }
  }

  override fun initialized() {
    sendCodeToReportBuffer(blockTerminalSession)
  }

  override fun commandFinished(event: CommandFinishedEvent) {
    sendCodeToReportBuffer(blockTerminalSession)
  }

  private fun sendCodeToReportBuffer(
    blockTerminalSession: BlockTerminalSession
  ) {
    // Bash is disabled because PREEXEC and PRECMD is not working correctly for keybindings.
    if (blockTerminalSession.shellIntegration.shellType !in setOf(ShellType.ZSH/*, ShellType.BASH*/)) {
      // Other shell types are not supported yet.
      return
    }

    blockTerminalSession.terminalStarterFuture.thenAccept { terminalStarter ->
      terminalStarter?.let {
        // TODO @vshefer replace F12 with more appropriate key sequence
        val bytes = blockTerminalSession.controller.getCodeForKey(KeyEvent.VK_F12, 0)!!
        blockTerminalSession.commandExecutionManager.sendKeyBinding(KeyBinding(bytes))
      }
    }
  }
}
