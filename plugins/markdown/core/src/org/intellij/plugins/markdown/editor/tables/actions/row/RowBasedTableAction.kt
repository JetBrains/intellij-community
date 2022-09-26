// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.actions.row

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.editor.tables.TableUtils
import org.intellij.plugins.markdown.editor.tables.actions.TableActionKeys
import org.intellij.plugins.markdown.lang.MarkdownLanguageUtils.isMarkdownLanguage
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableRow
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableSeparatorRow

/**
 * Same as [org.intellij.plugins.markdown.editor.tables.actions.column.ColumnBasedTableAction].
 *
 * By default, table actions are intended to be updated on BGT.
 */
internal abstract class RowBasedTableAction(private val considerSeparatorRow: Boolean = false) : AnAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val editor = event.getRequiredData(CommonDataKeys.EDITOR)
    val file = event.getRequiredData(CommonDataKeys.PSI_FILE)
    val offset = event.getRequiredData(CommonDataKeys.CARET).offset
    val document = editor.document
    val tableAndRow = findTableAndRow(event, file, document, offset)
    requireNotNull(tableAndRow)
    val (table, row) = tableAndRow
    performAction(editor, table, row)
  }

  @Suppress("DuplicatedCode")
  override fun update(event: AnActionEvent) {
    val project = event.getData(CommonDataKeys.PROJECT)
    val editor = event.getData(CommonDataKeys.EDITOR)
    val file = event.getData(CommonDataKeys.PSI_FILE)
    val offset = event.getData(CommonDataKeys.CARET)?.offset

    if (project == null
        || editor == null
        || file == null
        || offset == null
        || !file.language.isMarkdownLanguage()) {
      event.presentation.isEnabledAndVisible = false
      return
    }

    val document = editor.document
    val tableAndRow = findTableAndRow(event, file, document, offset)
    event.presentation.isEnabledAndVisible = tableAndRow != null
    if (tableAndRow != null) {
      val (table, row) = tableAndRow
      update(event, table, row)
    }
    else {
      update(event, null, null)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  protected abstract fun performAction(editor: Editor, table: MarkdownTable, rowElement: PsiElement)

  protected open fun update(event: AnActionEvent, table: MarkdownTable?, rowElement: PsiElement?) = Unit

  private fun findTableAndRow(event: AnActionEvent, file: PsiFile, document: Document, offset: Int): Pair<MarkdownTable, PsiElement>? {
    return when {
      considerSeparatorRow -> findTableAndRow(event, file, document, offset, ::findRowOrSeparator)
      else -> findTableAndRow(event, file, document, offset, ::findRow)
    }
  }

  protected open fun findRowOrSeparator(file: PsiFile, document: Document, offset: Int): PsiElement? {
    return TableUtils.findRowOrSeparator(file, offset)
  }

  protected open fun findRow(file: PsiFile, document: Document, offset: Int): PsiElement? {
    return TableUtils.findRow(file, offset)
  }

  private fun findTableAndRow(
    event: AnActionEvent,
    file: PsiFile,
    document: Document,
    offset: Int,
    rowGetter: (PsiFile, Document, Int) -> PsiElement?
  ): Pair<MarkdownTable, PsiElement>? {
    val elementFromEvent = event.getData(TableActionKeys.ELEMENT)?.get()
    if (elementFromEvent != null) {
      val table = obtainParentTable(elementFromEvent)
      if (table != null) {
        return table to elementFromEvent
      }
    }
    val row = rowGetter(file, document, offset)?.takeIf { it.isValid }
    val table = row?.let(::obtainParentTable)?.takeIf { it.isValid }
    return when {
      table != null -> table to row
      else -> null
    }
  }

  private fun obtainParentTable(element: PsiElement): MarkdownTable? {
    return when (element) {
      is MarkdownTableRow -> element.parentTable
      is MarkdownTableSeparatorRow -> element.parentTable
      else -> null
    }
  }
}
