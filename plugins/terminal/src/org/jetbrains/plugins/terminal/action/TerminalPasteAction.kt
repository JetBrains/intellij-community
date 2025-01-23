// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.jediterm.terminal.TerminalOutputStream
import org.jetbrains.plugins.terminal.block.TerminalPromotedDumbAwareAction
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalInput
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.editor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isAlternateBufferEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isAlternateBufferModelEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputModelEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.outputController
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.promptController
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.selectionController
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.simpleTerminalController
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalFocusModel
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalInput
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalSession
import java.awt.datatransfer.DataFlavor

internal class TerminalPasteAction : TerminalPromotedDumbAwareAction(), ActionRemoteBehaviorSpecification.Disabled {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.editor as? EditorEx ?: return
    val input = e.terminalInput
    when {
      editor.isPromptEditor -> pasteIntoPrompt(e, e.dataContext)
      editor.isAlternateBufferEditor -> pasteIntoTerminalSession(e)
      editor.isOutputEditor -> {
        if (e.terminalSession?.model?.isCommandRunning == true) {
          pasteIntoTerminalSession(e)
        }
        else {
          // do not pass data context of output editor to prompt
          pasteIntoPrompt(e, dataContext = null)
        }
      }
      input != null -> paseIntoInput(input)
    }
  }

  override fun update(e: AnActionEvent) {
    val editor = e.editor
    e.presentation.isEnabledAndVisible = editor != null && (
      editor.isPromptEditor || editor.isOutputEditor || editor.isAlternateBufferEditor || // gen1
      editor.isOutputModelEditor || editor.isAlternateBufferModelEditor // gen2
    )
  }

  private fun pasteIntoPrompt(e: AnActionEvent, dataContext: DataContext?) {
    val promptController = e.promptController ?: return
    e.terminalFocusModel?.focusPrompt()
    promptController.performPaste(dataContext)
  }

  private fun pasteIntoTerminalSession(e: AnActionEvent) {
    val session = e.terminalSession ?: error("No TerminalSession in the data context")

    // clear text and block selection if it is an output editor
    e.selectionController?.clearSelection()

    // clear text selection if it is an alternate buffer editor
    e.simpleTerminalController?.clearTextSelection()

    e.outputController?.scrollToBottom()
    doPasteIntoTerminalSession(session.terminalOutputStream)
  }

  private fun doPasteIntoTerminalSession(output: TerminalOutputStream) {
    val text = getClipboardText() ?: return
    if (text.isNotEmpty()) {
      output.sendString(text, false)
    }
  }

  private fun paseIntoInput(input: TerminalInput) {
    var text = getClipboardText() ?: return
    // The following logic was borrowed from JediTerm.
    // Sanitize clipboard text to use CR as the line separator.
    // See https://github.com/JetBrains/jediterm/issues/136.
    if (text.isNotEmpty()) {
      // On Windows, Java automatically does this CRLF->LF sanitization, but
      // other terminals on Unix typically also do this sanitization.
      if (!SystemInfoRt.isWindows) {
        text = text.replace("\r\n", "\n")
      }
      // Now convert this into what the terminal typically expects.
      text = text.replace("\n", "\r")
      input.sendBracketedString(text)
    }
  }

  private fun getClipboardText(): String? {
    val content = CopyPasteManager.getInstance().contents ?: return null
    val text = try {
      content.getTransferData(DataFlavor.stringFlavor) as String
    }
    catch (t: Throwable) {
      thisLogger().error("Failed to get text from clipboard", t)
      return null
    }
    return text
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
