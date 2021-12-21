// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.actions.column

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Editor
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.selectColumn
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable

internal abstract class SelectCurrentColumnAction(private val wholeColumn: Boolean): ColumnBasedTableAction() {
  override fun performAction(editor: Editor, table: MarkdownTable, columnIndex: Int) {
    val caretOffset = editor.caretModel.currentCaret.offset
    val insideHeader = table.headerRow?.textRange?.contains(caretOffset) == true
    runWriteAction {
      executeCommand(table.project) {
        table.selectColumn(
          editor,
          columnIndex,
          withHeader = insideHeader || wholeColumn,
          withSeparator = wholeColumn,
          withBorders = true
        )
      }
    }
  }

  class SelectContentCells: SelectCurrentColumnAction(wholeColumn = true)
}
