// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.actions.row

import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.editor.tables.selectRow
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable

internal class SelectRowAction: RowBasedTableAction(considerSeparatorRow = true) {
  override fun performAction(editor: Editor, table: MarkdownTable, rowElement: PsiElement) {
    executeCommand(rowElement.project) {
      table.selectRow(editor, rowElement)
    }
  }
}
