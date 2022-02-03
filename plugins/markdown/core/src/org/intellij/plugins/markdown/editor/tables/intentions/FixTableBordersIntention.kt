// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.tables.TableFormattingUtils
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.hasCorrectBorders
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.isCorrectlyFormatted
import org.intellij.plugins.markdown.editor.tables.TableProps
import org.intellij.plugins.markdown.editor.tables.TableUtils
import org.intellij.plugins.markdown.editor.tables.TableUtils.columnsCount
import org.intellij.plugins.markdown.editor.tables.TableUtils.separatorRow
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElementFactory
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableRow
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableSeparatorRow
import org.intellij.plugins.markdown.util.hasType

internal class FixTableBordersIntention: PsiElementBaseIntentionAction() {
  override fun getFamilyName(): String = text

  override fun getText(): String {
    return MarkdownBundle.message("markdown.fix.table.borders.intention.text")
  }

  override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
    val table = TableUtils.findTable(element)
    return table?.hasCorrectBorders() == false
  }

  override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
    val table = TableUtils.findTable(element)!!
    val rows = table.getRows(true)
    val columnsCount = rows.asSequence().map { it.columnsCount }.maxOrNull() ?: 0
    for (row in rows) {
      fixRow(row, columnsCount)
    }
    table.separatorRow?.let {
      if (!it.hasCorrectBorders()) {
        fixSeparatorRow(it)
      }
    }
    if (editor != null && !table.isCorrectlyFormatted()) {
      val document = editor.document
      PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)
      TableFormattingUtils.reformatAllColumns(table, document, trimToMaxContent = true, preventExpand = false)
    }
  }

  private fun fixSeparatorRow(separatorRow: MarkdownTableSeparatorRow) {
    val currentText = separatorRow.text
    val newText = buildString {
      if (!currentText.startsWith(TableProps.SEPARATOR_CHAR)) {
        append(TableProps.SEPARATOR_CHAR)
      }
      append(currentText)
      if (!currentText.endsWith(TableProps.SEPARATOR_CHAR)) {
        append(TableProps.SEPARATOR_CHAR)
      }
    }
    separatorRow.replace(MarkdownPsiElementFactory.createTableSeparatorRow(separatorRow.project, newText))
  }

  private fun fixRow(row: MarkdownTableRow, columnsCount: Int) {
    val project = row.project
    if (row.firstChild?.hasType(MarkdownTokenTypes.TABLE_SEPARATOR) == false) {
      row.addBefore(MarkdownPsiElementFactory.createTableSeparator(project), row.firstChild)
    }
    if (row.lastChild?.hasType(MarkdownTokenTypes.TABLE_SEPARATOR) == false) {
      row.addAfter(MarkdownPsiElementFactory.createTableSeparator(project), row.lastChild)
    }
    val columnsDiff = columnsCount - row.columnsCount
    repeat(columnsDiff) {
      val (cell, separator) = MarkdownPsiElementFactory.createTableCell(project, " ".repeat(TableProps.MIN_CELL_WIDTH))
      row.addRange(cell, separator)
    }
  }
}
