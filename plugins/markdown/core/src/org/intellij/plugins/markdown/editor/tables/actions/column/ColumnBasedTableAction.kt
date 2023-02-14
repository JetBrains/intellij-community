// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.actions.column

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.editor.tables.TableUtils
import org.intellij.plugins.markdown.editor.tables.actions.TableActionKeys
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.MarkdownLanguageUtils.isMarkdownLanguage
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
    val offset = event.getRequiredData(CommonDataKeys.CARET).offset
    val document = editor.document
    val (table, columnIndex) = findTableAndIndex(event, file, document, offset)
    requireNotNull(table)
    requireNotNull(columnIndex)
    performAction(editor, table, columnIndex)
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
    val (table, columnIndex) = findTableAndIndex(event, file, document, offset)
    event.presentation.isEnabledAndVisible = table != null && columnIndex != null
    update(event, table, columnIndex)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  protected abstract fun performAction(editor: Editor, table: MarkdownTable, columnIndex: Int)

  protected open fun update(event: AnActionEvent, table: MarkdownTable?, columnIndex: Int?) = Unit

  private fun findTableAndIndex(event: AnActionEvent, file: PsiFile, document: Document, offset: Int): Pair<MarkdownTable?, Int?> {
    return findTableAndIndex(event, file, document, offset, ::findTable, ::findColumnIndex)
  }

  protected open fun findTable(file: PsiFile, document: Document, offset: Int): MarkdownTable? {
    return TableUtils.findTable(file, offset)
  }

  protected open fun findColumnIndex(file: PsiFile, document: Document, offset: Int): Int? {
    return TableUtils.findCellIndex(file, offset)
  }

  companion object {
    fun findTableAndIndex(
      event: AnActionEvent,
      file: PsiFile,
      document: Document,
      offset: Int,
      tableGetter: (PsiFile, Document, Int) -> MarkdownTable?,
      columnIndexGetter: (PsiFile, Document, Int) -> Int?
    ): Pair<MarkdownTable?, Int?> {
      val tableFromEvent = event.getData(TableActionKeys.ELEMENT)?.get() as? MarkdownTable
      val indexFromEvent = event.getData(TableActionKeys.COLUMN_INDEX)
      if (tableFromEvent != null && indexFromEvent != null) {
        return tableFromEvent to indexFromEvent
      }
      val table = tableGetter(file, document, offset)?.takeIf { it.isValid }
      val index = columnIndexGetter(file, document, offset)
      return table to index
    }

    fun findTableAndIndex(event: AnActionEvent, file: PsiFile, document: Document, offset: Int): Pair<MarkdownTable?, Int?> {
      return findTableAndIndex(
        event,
        file,
        document,
        offset,
        tableGetter = { file, document, offset -> TableUtils.findTable(file, offset) },
        columnIndexGetter = { file, document, offset -> TableUtils.findCellIndex(file, offset) }
      )
    }
  }
}
