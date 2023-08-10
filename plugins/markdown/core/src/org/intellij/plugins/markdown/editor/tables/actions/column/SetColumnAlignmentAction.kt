// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.actions.column

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.hasCorrectBorders
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.updateColumnAlignment
import org.intellij.plugins.markdown.editor.tables.TableUtils.getColumnAlignment
import org.intellij.plugins.markdown.lang.MarkdownLanguageUtils.isMarkdownLanguage
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableSeparatorRow

internal abstract class SetColumnAlignmentAction(private val alignment: MarkdownTableSeparatorRow.CellAlignment): ToggleAction() {
  /**
   * Presentation updates are here instead of [update], so the caret offset can be taken only once.
   */
  override fun isSelected(event: AnActionEvent): Boolean {
    val editor = event.getData(CommonDataKeys.EDITOR)
    val file = event.getData(CommonDataKeys.PSI_FILE)
    val offset = event.getData(CommonDataKeys.CARET)?.offset

    if (editor == null
        || file == null
        || offset == null
        || !file.language.isMarkdownLanguage()) {
      event.presentation.isEnabledAndVisible = false
      return false
    }

    val document = editor.document
    val (table, columnIndex) = ColumnBasedTableAction.findTableAndIndex(event, file, document, offset)
    event.presentation.isEnabledAndVisible = table?.hasCorrectBorders() == true
    return when {
      table == null || columnIndex == null -> false
      else -> table.getColumnAlignment(columnIndex) == alignment
    }
  }

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    val editor = event.getData(CommonDataKeys.EDITOR)
    val file = event.getData(CommonDataKeys.PSI_FILE)
    val offset = event.getData(CommonDataKeys.CARET)?.offset
    if (editor == null || file == null || offset == null) {
      return
    }
    val document = editor.document
    val (table, columnIndex) = ColumnBasedTableAction.findTableAndIndex(event, file, document, offset)
    if (table != null && columnIndex != null) {
      runWriteAction {
        executeCommand(table.project) {
          val actualAlignment = when {
            state -> alignment
            else -> MarkdownTableSeparatorRow.CellAlignment.NONE
          }
          table.updateColumnAlignment(editor.document, columnIndex, actualAlignment)
        }
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  //class None: SetColumnAlignmentAction(MarkdownTableSeparatorRow.CellAlignment.NONE)

  class Left: SetColumnAlignmentAction(MarkdownTableSeparatorRow.CellAlignment.LEFT)

  class Center: SetColumnAlignmentAction(MarkdownTableSeparatorRow.CellAlignment.CENTER)

  class Right: SetColumnAlignmentAction(MarkdownTableSeparatorRow.CellAlignment.RIGHT)
}
