// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.actions.column

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.editor.tables.TableUtils
import org.intellij.plugins.markdown.editor.tables.actions.TableActionKeys
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable

/**
 * Base class for actions operating on a single column.
 *
 * Implementations should override performAction and update methods which take current editor, table and column index.
 *
 * Could be invoked from two contexts: normally from the editor or from the table inlay toolbar.
 * In the latter case current column index, and it's parent table are taken from the event's data context (see [TableActionKeys]).
 * If table and column index are not found in even's data context, then we consider that action was invoked normally
 * (e.g. from search everywhere) and compute column index and table element based on the position of current caret.
 *
 * See [ColumnBasedTableAction.Companion.findTableAndIndex].
 */
internal abstract class ColumnBasedTableAction: AnAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val editor = event.getRequiredData(CommonDataKeys.EDITOR)
    val file = event.getRequiredData(CommonDataKeys.PSI_FILE)
    val (table, columnIndex) = findTableAndIndex(event, file, editor)
    requireNotNull(table)
    requireNotNull(columnIndex)
    performAction(editor, table, columnIndex)
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
    val (table, columnIndex) = findTableAndIndex(event, file, editor)
    event.presentation.isEnabledAndVisible = table != null && columnIndex != null
    update(event, table, columnIndex)
  }

  protected abstract fun performAction(editor: Editor, table: MarkdownTable, columnIndex: Int)

  protected open fun update(event: AnActionEvent, table: MarkdownTable?, columnIndex: Int?) = Unit

  private fun findTableAndIndex(event: AnActionEvent, file: PsiFile, editor: Editor): Pair<MarkdownTable?, Int?> {
    return findTableAndIndex(event, file, editor, ::findTable, ::findColumnIndex)
  }

  protected open fun findTable(file: PsiFile, editor: Editor): MarkdownTable? {
    return TableUtils.findTable(file, editor.caretModel.currentCaret.offset)
  }

  protected open fun findColumnIndex(file: PsiFile, editor: Editor): Int? {
    return TableUtils.findCellIndex(file, editor.caretModel.currentCaret.offset)
  }

  companion object {
    fun findTableAndIndex(
      event: AnActionEvent,
      file: PsiFile,
      editor: Editor,
      tableGetter: (PsiFile, Editor) -> MarkdownTable?,
      columnIndexGetter: (PsiFile, Editor) -> Int?
    ): Pair<MarkdownTable?, Int?> {
      val tableFromEvent = event.getData(TableActionKeys.ELEMENT)?.get() as? MarkdownTable
      val indexFromEvent = event.getData(TableActionKeys.COLUMN_INDEX)
      if (tableFromEvent != null && indexFromEvent != null) {
        return tableFromEvent to indexFromEvent
      }
      val table = tableGetter(file, editor)?.takeIf { it.isValid }
      val index = columnIndexGetter(file, editor)
      return table to index
    }

    fun findTableAndIndex(event: AnActionEvent, file: PsiFile, editor: Editor): Pair<MarkdownTable?, Int?> {
      return findTableAndIndex(
        event,
        file,
        editor,
        { file, editor -> TableUtils.findTable(file, editor.caretModel.currentCaret.offset) },
        { file, editor -> TableUtils.findCellIndex(file, editor.caretModel.currentCaret.offset) }
      )
    }
  }
}
