// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.hasCorrectPadding
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.hasValidAlignment
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.updateAlignment
import org.intellij.plugins.markdown.editor.tables.TableUtils
import org.intellij.plugins.markdown.editor.tables.TableUtils.getColumnAlignment

internal class FixCellAlignmentIntention: PsiElementBaseIntentionAction() {
  override fun getFamilyName(): String = text

  override fun getText(): String {
    return MarkdownBundle.message("markdown.fix.cell.alignment.intention.text")
  }

  override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
    val cell = TableUtils.findCell(element)
    if (editor == null || cell == null || cell.parentTable == null) {
      return false
    }
    return !cell.hasCorrectPadding() || !cell.hasValidAlignment()
  }

  override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
    requireNotNull(editor)
    val cell = TableUtils.findCell(element) ?: return
    val expectedAlignment = cell.parentTable?.getColumnAlignment(cell.columnIndex) ?: return
    executeCommand(project) {
      cell.updateAlignment(editor.document, expectedAlignment)
    }
  }
}
