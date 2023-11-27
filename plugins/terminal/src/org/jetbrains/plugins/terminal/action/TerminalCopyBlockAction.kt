// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import org.jetbrains.plugins.terminal.exp.CommandBlock
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.editor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isOutputEditor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.selectionController
import org.jetbrains.plugins.terminal.exp.TerminalPromotedDumbAwareAction

class TerminalCopyBlockAction : TerminalPromotedDumbAwareAction(), ActionRemoteBehaviorSpecification.Disabled {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.editor ?: return
    val selectionController = e.selectionController ?: return
    val selectedText = getBlocksText(editor, selectionController.selectedBlocks)
    if (selectedText.isNotEmpty()) {
      CopyPasteManager.copyTextToClipboard(selectedText)
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.editor?.isOutputEditor == true && e.selectionController?.primarySelection != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  private fun getBlocksText(editor: Editor, blocks: List<CommandBlock>): String {
    val sortedBlocks = blocks.sortedBy { it.startOffset }
    val builder = StringBuilder()
    for (index in sortedBlocks.indices) {
      val block = sortedBlocks[index]
      builder.append(editor.document.getText(block.textRange))
      if (index != sortedBlocks.lastIndex) {
        builder.append('\n')
      }
    }
    return builder.toString()
  }
}
