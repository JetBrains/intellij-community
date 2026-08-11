// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.codeInsight.editorActions.CopyHandler
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCopyPasteHelper
import com.intellij.openapi.editor.EditorCopyPasteHelper.CopyPasteOptions
import com.intellij.openapi.editor.actions.CopyAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isAlternateBufferEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isAlternateBufferModelEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputModelEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalEditor
import java.awt.datatransfer.Transferable

internal class TerminalCopyTextAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.terminalEditor ?: return
    val selectionToCopy = CopyAction.SelectionToCopy(CopyPasteOptions.DEFAULT, null)
    CopyAction.copyToClipboard(editor, TerminalTransferableProvider(), selectionToCopy)
  }

  override fun update(e: AnActionEvent) {
    val editor = e.terminalEditor
    val isVisible =
      editor != null &&
      (
        editor.isPromptEditor || editor.isOutputEditor || editor.isAlternateBufferEditor || // gen1
        editor.isOutputModelEditor || editor.isAlternateBufferModelEditor // gen2
      )
    val isEnabled = isVisible && editor.selectionModel.hasSelection()
    e.presentation.isVisible = isVisible
    e.presentation.isEnabled = isEnabled
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

private class TerminalTransferableProvider : CopyAction.TransferableProvider {
  override fun getSelection(
    editor: Editor,
    options: CopyPasteOptions,
  ): Transferable? {
    val project = editor.project
    val psiFile = project?.let { PsiDocumentManager.getInstance(it).getPsiFile(editor.document) }
    return if (psiFile != null) {
      CopyHandler.getSelection(editor, project, psiFile, options)
    }
    else EditorCopyPasteHelper.getInstance().getSelectionTransferable(editor, options)
  }
}