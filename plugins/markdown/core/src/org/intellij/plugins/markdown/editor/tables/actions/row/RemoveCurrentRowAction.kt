// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.actions.row

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.editor.tables.TableUtils.isHeaderRow
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableRow

internal class RemoveCurrentRowAction: RowBasedTableAction(considerSeparatorRow = false) {
  override fun performAction(editor: Editor, table: MarkdownTable, rowElement: PsiElement) {
    runWriteAction {
      executeCommand(rowElement.project) {
        rowElement.delete()
      }
    }
  }

  override fun findRow(file: PsiFile, document: Document, offset: Int): PsiElement? {
    val row = super.findRow(file, document, offset)
    return row?.takeUnless { (it as? MarkdownTableRow)?.isHeaderRow == true }
  }
}
