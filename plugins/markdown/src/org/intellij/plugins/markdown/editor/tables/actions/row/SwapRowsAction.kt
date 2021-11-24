// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.actions.row

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.siblings
import org.intellij.plugins.markdown.editor.tables.TableUtils.isHeaderRow
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableImpl
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableRowImpl

internal abstract class SwapRowsAction(private val swapWithAbove: Boolean): RowBasedTableAction(considerSeparatorRow = false) {
  override fun performAction(editor: Editor, table: MarkdownTableImpl, rowElement: PsiElement) {
    rowElement as MarkdownTableRowImpl
    val otherRow = findOtherRow(rowElement)
    requireNotNull(otherRow)
    runWriteAction {
      val currentRowClone = rowElement.copy()
      executeCommand(rowElement.project) {
        rowElement.replace(otherRow)
        otherRow.replace(currentRowClone)
      }
    }
  }

  override fun update(event: AnActionEvent, table: MarkdownTableImpl?, rowElement: PsiElement?) {
    super.update(event, table, rowElement)
    event.presentation.isEnabled = (rowElement as? MarkdownTableRowImpl)?.let(::findOtherRow) != null
  }

  private fun findOtherRow(row: MarkdownTableRowImpl): MarkdownTableRowImpl? {
    return row.siblings(forward = !swapWithAbove, withSelf = false).filterIsInstance<MarkdownTableRowImpl>().find { !it.isHeaderRow }
  }

  override fun findRow(file: PsiFile, editor: Editor): PsiElement? {
    return super.findRow(file, editor).takeUnless { (it as? MarkdownTableRowImpl)?.isHeaderRow == true }
  }

  class SwapWithAbove: SwapRowsAction(swapWithAbove = true)

  class SwapWithBelow: SwapRowsAction(swapWithAbove = false)
}
