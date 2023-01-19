// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.actions.column

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.hasCorrectBorders
import org.intellij.plugins.markdown.lang.MarkdownLanguageUtils.isMarkdownLanguage

internal class TableColumnAlignmentActionsGroup: DefaultActionGroup() {
  override fun update(event: AnActionEvent) {
    val editor = event.getData(CommonDataKeys.EDITOR)
    val file = event.getData(CommonDataKeys.PSI_FILE)
    val offset = event.getData(CommonDataKeys.CARET)?.offset

    if (editor == null
        || file == null
        || offset == null
        || !file.language.isMarkdownLanguage()) {
      event.presentation.isEnabledAndVisible = false
      return
    }

    val document = editor.document
    val (table, columnIndex) = ColumnBasedTableAction.findTableAndIndex(event, file, document, offset)
    event.presentation.isEnabledAndVisible = table != null && columnIndex != null && table.hasCorrectBorders()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
