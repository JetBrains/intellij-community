// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.actions.row

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.editor.tables.TableUtils.isHeaderRow
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElementFactory
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableRow
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableSeparatorRow

internal abstract class InsertRowAction(private val insertAbove: Boolean): RowBasedTableAction(considerSeparatorRow = true) {
  override fun performAction(editor: Editor, table: MarkdownTable, rowElement: PsiElement) {
    runWriteAction {
      val widths = obtainCellsWidths(rowElement)
      val newRow = MarkdownPsiElementFactory.createTableEmptyRow(table.project, widths)
      require(rowElement.parent == table)
      executeCommand(rowElement.project) {
        when {
          insertAbove -> table.addRangeBefore(newRow, newRow.nextSibling, rowElement)
          else -> table.addRangeAfter(newRow.prevSibling, newRow, rowElement)
        }
      }
    }
  }

  private fun obtainCellsWidths(element: PsiElement): Collection<Int> {
    return when (element) {
      is MarkdownTableRow -> element.cells.map { it.textLength }
      is MarkdownTableSeparatorRow -> element.cellsRanges.map { it.length }
      else -> error("element should be either MarkdownTableRow or MarkdownTableSeparatorRow")
    }
  }

  override fun findRowOrSeparator(file: PsiFile, editor: Editor): PsiElement? {
    val element = super.findRowOrSeparator(file, editor) ?: return null
    return when {
      (element as? MarkdownTableRow)?.isHeaderRow == true -> null
      insertAbove && element is MarkdownTableSeparatorRow -> null
      else -> element
    }
  }

  class InsertAbove: InsertRowAction(insertAbove = true)

  class InsertBelow: InsertRowAction(insertAbove = false)
}
