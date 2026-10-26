// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.markdown.backend.intentions

import com.intellij.codeInspection.IntentionAndQuickFixAction
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.tables.TableFormattingUtils
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.hasCorrectBorders
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.isCorrectlyFormatted
import org.intellij.plugins.markdown.editor.tables.TableProps
import org.intellij.plugins.markdown.editor.tables.TableUtils
import org.intellij.plugins.markdown.editor.tables.TableUtils.calculateActualTextRange
import org.intellij.plugins.markdown.editor.tables.TableUtils.columnsCount
import org.intellij.plugins.markdown.editor.tables.TableUtils.getTableStyle
import org.intellij.plugins.markdown.editor.tables.TableUtils.separatorRow
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElementFactory
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableRow
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableSeparatorRow
import org.intellij.plugins.markdown.lang.psi.util.hasType

internal class FixTableBordersIntention: IntentionAndQuickFixAction() {
  override fun getName(): String = MarkdownBundle.message("markdown.fix.table.borders.intention.text")

  override fun getFamilyName(): String = name

  override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile): Boolean {
    val editor = editor ?: return false
    return TableUtils.findTable(psiFile, editor.caretModel.offset)?.hasCorrectBorders() == false
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val table = TableUtils.findTable(descriptor.psiElement) ?: return
    fixTable(table, null)
  }

  override fun applyFix(project: Project, psiFile: PsiFile, editor: Editor?) {
    val editor = editor ?: return
    val table = TableUtils.findTable(psiFile, editor.caretModel.offset) ?: return
    fixTable(table, editor)
  }

  private fun fixTable(table: MarkdownTable, editor: Editor?) {
    val rows = table.getRows(true)
    val columnsCount = rows.maxOfOrNull { it.columnsCount } ?: 0
    for (row in rows) {
      fixRow(row, columnsCount)
    }
    table.separatorRow?.let {
      if (!it.hasCorrectBorders()) {
        fixSeparatorRow(it)
      }
    }
    val project = table.project
    val file = table.containingFile
    val tableStyle = getTableStyle(file)
    val document = editor?.document ?: file.viewProvider.document ?: return
    if (!table.isCorrectlyFormatted(tableStyle)) {
      PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)
      TableFormattingUtils.reformatAllColumns(
        table,
        document,
        tableStyle,
        trimToMaxContent = true,
        preventExpand = false,
      )
    }
  }

  private fun fixSeparatorRow(separatorRow: MarkdownTableSeparatorRow) {
    val actualRange = separatorRow.calculateActualTextRange().shiftLeft(separatorRow.startOffset)
    val prefix = separatorRow.text.substring(0, actualRange.startOffset)
    val currentText = actualRange.substring(separatorRow.text)
    val newText = buildString {
      append(prefix)
      if (!currentText.startsWith(TableProps.SEPARATOR_CHAR)) {
        append(TableProps.SEPARATOR_CHAR)
      }
      append(currentText)
      if (!currentText.endsWith(TableProps.SEPARATOR_CHAR)) {
        append(TableProps.SEPARATOR_CHAR)
      }
    }
    separatorRow.replaceWithText(newText)
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
      val (cell, separator) = MarkdownPsiElementFactory.createTableCell(project, " ".repeat(5))
      row.addRange(cell, separator)
    }
  }
}
