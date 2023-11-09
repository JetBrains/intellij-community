// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.ide.CopyPasteManager
import com.jediterm.terminal.TerminalOutputStream
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.editor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isAlternateBufferEditor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isOutputEditor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.promptController
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.selectionController
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.simpleTerminalController
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.terminalFocusModel
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.terminalSession
import org.jetbrains.plugins.terminal.exp.TerminalPromotedDumbAwareAction
import java.awt.datatransfer.DataFlavor

class TerminalPasteAction : TerminalPromotedDumbAwareAction(), ActionRemoteBehaviorSpecification.Disabled {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.editor as? EditorEx ?: return
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
    }
  }

  override fun update(e: AnActionEvent) {
    val editor = e.editor
    e.presentation.isEnabledAndVisible = editor != null && (editor.isPromptEditor || editor.isOutputEditor || editor.isAlternateBufferEditor)
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

    session.terminalStarterFuture.thenAccept {
      doPasteIntoTerminalSession(it)
    }
  }

  private fun doPasteIntoTerminalSession(output: TerminalOutputStream) {
    val content = CopyPasteManager.getInstance().contents ?: return
    val text = try {
      content.getTransferData(DataFlavor.stringFlavor) as String
    }
    catch (t: Throwable) {
      thisLogger().error("Failed to get text from clipboard", t)
      return
    }
    if (text.isNotEmpty()) {
      output.sendString(text, false)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}