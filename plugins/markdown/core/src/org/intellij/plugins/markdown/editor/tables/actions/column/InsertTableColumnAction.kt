// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.actions.column

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.hasCorrectBorders
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.insertColumn
import org.intellij.plugins.markdown.editor.tables.TableUtils
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable

internal abstract class InsertTableColumnAction(private val insertAfter: Boolean = true): ColumnBasedTableAction() {
  override fun performAction(editor: Editor, table: MarkdownTable, columnIndex: Int) {
    runWriteAction {
      executeCommand(table.project) {
        table.insertColumn(editor.document, columnIndex, after = insertAfter)
      }
    }
  }

  override fun findColumnIndex(file: PsiFile, editor: Editor): Int? {
    val caretOffset = editor.caretModel.currentCaret.offset
    val cellIndex = TableUtils.findCellIndex(file, caretOffset)
    if (cellIndex != null) {
      return cellIndex
    }
    return TableUtils.findSeparatorRow(file, caretOffset)?.getColumnIndexFromOffset(caretOffset)
  }

  override fun update(event: AnActionEvent, table: MarkdownTable?, columnIndex: Int?) {
    super.update(event, table, columnIndex)
    event.presentation.isEnabledAndVisible = table?.hasCorrectBorders() == true
  }

  class InsertBefore: InsertTableColumnAction(insertAfter = false)

  class InsertAfter: InsertTableColumnAction(insertAfter = true)
}
