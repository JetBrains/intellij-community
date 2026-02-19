// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.actions.column

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Editor
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.swapCells
import org.intellij.plugins.markdown.editor.tables.TableUtils.columnsIndices
import org.intellij.plugins.markdown.editor.tables.TableUtils.getColumnCells
import org.intellij.plugins.markdown.editor.tables.TableUtils.separatorRow
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable

internal abstract class SwapColumnsAction(private val swapWithLeftColumn: Boolean): ColumnBasedTableAction() {
  override fun performAction(editor: Editor, table: MarkdownTable, columnIndex: Int) {
    runWriteAction {
      val otherColumnIndex = findOtherColumnIndex(table, columnIndex)
      requireNotNull(otherColumnIndex)
      val currentColumn = table.getColumnCells(columnIndex, withHeader = true)
      val otherColumn = table.getColumnCells(otherColumnIndex, withHeader = true)
      executeCommand(table.project) {
        for ((current, other) in currentColumn.zip(otherColumn)) {
          val currentCopy = current.copy()
          current.replace(other)
          other.replace(currentCopy)
        }
        table.separatorRow?.swapCells(columnIndex, otherColumnIndex)
      }
    }
  }

  private fun findOtherColumnIndex(table: MarkdownTable, columnIndex: Int): Int? {
    val indices = table.columnsIndices
    return when {
      swapWithLeftColumn && columnIndex - 1 >= 0 -> columnIndex - 1
      !swapWithLeftColumn && columnIndex + 1 <= indices.last -> columnIndex + 1
      else -> null
    }
  }

  override fun update(event: AnActionEvent, table: MarkdownTable?, columnIndex: Int?) {
    super.update(event, table, columnIndex)
    if (table != null && columnIndex != null) {
      event.presentation.isEnabled = findOtherColumnIndex(table, columnIndex) != null
    }
  }

  class SwapWithLeftColumn: SwapColumnsAction(swapWithLeftColumn = true)

  class SwapWithRightColumn: SwapColumnsAction(swapWithLeftColumn = false)
}
