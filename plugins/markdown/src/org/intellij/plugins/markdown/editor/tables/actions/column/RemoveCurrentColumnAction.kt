// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.actions.column

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Editor
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.removeColumn
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable

internal class RemoveCurrentColumnAction: ColumnBasedTableAction() {
  override fun performAction(editor: Editor, table: MarkdownTable, columnIndex: Int) {
    runWriteAction {
      executeCommand(table.project) {
        table.removeColumn(columnIndex)
      }
    }
  }
}
