// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.actions.row

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.editor.tables.TableUtils
import org.intellij.plugins.markdown.editor.tables.actions.TableActionKeys
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableRow
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableSeparatorRow

/**
 * Same as [org.intellij.plugins.markdown.editor.tables.actions.column.ColumnBasedTableAction].
 */
internal abstract class RowBasedTableAction(private val considerSeparatorRow: Boolean = false): AnAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val editor = event.getRequiredData(CommonDataKeys.EDITOR)
    val file = event.getRequiredData(CommonDataKeys.PSI_FILE)
    val tableAndRow = findTableAndRow(event, file, editor)
    requireNotNull(tableAndRow)
    val (table, row) = tableAndRow
    performAction(editor, table, row)
  }

  @Suppress("DuplicatedCode")
  override fun update(event: AnActionEvent) {
    val project = event.getData(CommonDataKeys.PROJECT)
    val editor = event.getData(CommonDataKeys.EDITOR)
    val file = event.getData(CommonDataKeys.PSI_FILE)
    if (project == null || editor == null || file == null) {
      event.presentation.isEnabledAndVisible = false
      return
    }
    val tableAndRow = findTableAndRow(event, file, editor)
    event.presentation.isEnabledAndVisible = tableAndRow != null
    if (tableAndRow != null) {
      val (table, row) = tableAndRow
      update(event, table, row)
    } else {
      update(event, null, null)
    }
  }

  protected abstract fun performAction(editor: Editor, table: MarkdownTable, rowElement: PsiElement)

  protected open fun update(event: AnActionEvent, table: MarkdownTable?, rowElement: PsiElement?) = Unit

  private fun findTableAndRow(event: AnActionEvent, file: PsiFile, editor: Editor): Pair<MarkdownTable, PsiElement>? {
    return when {
      considerSeparatorRow -> findTableAndRow(event, file, editor, ::findRowOrSeparator)
      else -> findTableAndRow(event, file, editor, ::findRow)
    }
  }

  protected open fun findRowOrSeparator(file: PsiFile, editor: Editor): PsiElement? {
    return TableUtils.findRowOrSeparator(file, editor.caretModel.currentCaret.offset)
  }

  protected open fun findRow(file: PsiFile, editor: Editor): PsiElement? {
    return TableUtils.findRow(file, editor.caretModel.currentCaret.offset)
  }

  companion object {
    fun findTableAndRow(
      event: AnActionEvent,
      file: PsiFile,
      editor: Editor,
      rowGetter: (PsiFile, Editor) -> PsiElement?
    ): Pair<MarkdownTable, PsiElement>? {
      val elementFromEvent = event.getData(TableActionKeys.ELEMENT)?.get()
      if (elementFromEvent != null) {
        val table = obtainParentTable(elementFromEvent)
        if (table != null) {
          return table to elementFromEvent
        }
      }
      val row = rowGetter(file, editor)?.takeIf { it.isValid }
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

    fun findTableAndRow(event: AnActionEvent, file: PsiFile, editor: Editor): Pair<MarkdownTable, PsiElement>? {
      return findTableAndRow(event, file, editor) { fileToSearch, editorToSearch ->
        TableUtils.findRowOrSeparator(fileToSearch, editorToSearch.caretModel.currentCaret.offset)
      }
    }
  }
}
