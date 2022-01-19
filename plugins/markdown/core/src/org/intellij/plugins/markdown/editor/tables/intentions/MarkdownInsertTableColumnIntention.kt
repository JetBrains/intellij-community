// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.hasCorrectBorders
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.insertColumn
import org.intellij.plugins.markdown.editor.tables.TableUtils
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableSeparatorRow
import org.intellij.plugins.markdown.settings.MarkdownSettings

internal abstract class MarkdownInsertTableColumnIntention(private val insertAfter: Boolean): PsiElementBaseIntentionAction() {
  override fun getFamilyName(): String {
    return MarkdownBundle.message("markdown.insert.table.column.intention.family")
  }

  override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
    if (!MarkdownSettings.getInstance(project).isEnhancedEditingEnabled) {
      return false
    }
    val cell = TableUtils.findCell(element)
    return cell != null && editor != null && cell.parentTable?.hasCorrectBorders() == true
  }

  override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
    val cell = TableUtils.findCell(element)
    val table = cell?.parentTable
    if (cell == null || table == null || editor == null) {
      return
    }
    executeCommand(project) {
      table.insertColumn(editor.document, cell.columnIndex, insertAfter, alignment = MarkdownTableSeparatorRow.CellAlignment.LEFT)
    }
  }

  class InsertBefore: MarkdownInsertTableColumnIntention(insertAfter = false) {
    override fun getText(): String {
      return MarkdownBundle.message("markdown.insert.table.column.to.the.left.intention.text")
    }
  }

  class InsertAfter: MarkdownInsertTableColumnIntention(insertAfter = true) {
    override fun getText(): String {
      return MarkdownBundle.message("markdown.insert.table.column.to.the.right.intention.text")
    }
  }
}
