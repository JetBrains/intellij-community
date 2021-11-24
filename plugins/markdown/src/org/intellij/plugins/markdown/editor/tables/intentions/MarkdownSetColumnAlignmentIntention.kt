// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.hasCorrectBorders
import org.intellij.plugins.markdown.editor.tables.TableUtils
import org.intellij.plugins.markdown.settings.MarkdownSettings

internal class MarkdownSetColumnAlignmentIntention: PsiElementBaseIntentionAction() {
  override fun getFamilyName() = text

  override fun getText(): String {
    return MarkdownBundle.message("markdown.set.column.alignment.intention.text")
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
    invokeLater {
      val context = DataManager.getInstance().getDataContext(editor.component)
      val group = ActionManager.getInstance().getAction("Markdown.TableColumnActions.ColumnAlignmentActions") as? ActionGroup
      requireNotNull(group)
      val popup = JBPopupFactory.getInstance().createActionGroupPopup(
        MarkdownBundle.message("markdown.set.column.alignment.intention.popup.text"),
        group,
        context,
        JBPopupFactory.ActionSelectionAid.MNEMONICS,
        true
      )
      popup.showInFocusCenter()
    }
  }
}
