// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.editor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isAlternateBufferEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isAlternateBufferModelEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputModelEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isPromptEditor

internal class TerminalCopyTextAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent) {
    val selectedText = e.editor?.selectionModel?.selectedText ?: return
    CopyPasteManager.copyTextToClipboard(selectedText)
  }

  override fun update(e: AnActionEvent) {
    val editor = e.editor
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
