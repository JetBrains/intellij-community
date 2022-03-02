// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.removeColumn
import org.intellij.plugins.markdown.editor.tables.TableUtils
import org.intellij.plugins.markdown.settings.MarkdownSettings

internal class MarkdownRemoveColumnIntention: PsiElementBaseIntentionAction() {
  override fun getFamilyName() = text

  override fun getText(): String {
    return MarkdownBundle.message("markdown.remove.column.intention.text")
  }

  override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
    if (!MarkdownSettings.getInstance(project).isEnhancedEditingEnabled) {
      return false
    }
    val cell = TableUtils.findCell(element)
    return cell != null && cell.parentTable != null && editor != null
  }

  override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
    val cell = TableUtils.findCell(element)
    val table = cell?.parentTable
    if (cell == null || table == null || editor == null) {
      return
    }
    val columnIndex = cell.columnIndex
    executeCommand(table.project) {
      table.removeColumn(columnIndex)
    }
  }
}
