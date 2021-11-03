// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.tables.TableUtils
import org.intellij.plugins.markdown.editor.tables.TableUtils.isHeaderRow
import org.intellij.plugins.markdown.settings.MarkdownSettings

internal class MarkdownRemoveRowIntention: PsiElementBaseIntentionAction() {
  override fun getFamilyName() = text

  override fun getText(): String {
    return MarkdownBundle.message("markdown.remove.row.intention.text")
  }

  override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
    if (!MarkdownSettings.getInstance(project).isEnhancedEditingEnabled) {
      return false
    }
    return findContentNonHeaderRow(element) != null
  }

  override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
    val row = findContentNonHeaderRow(element) ?: return
    executeCommand(project) {
      row.delete()
    }
  }

  private fun findContentNonHeaderRow(element: PsiElement): PsiElement? {
    return TableUtils.findRow(element)?.takeUnless { it.isHeaderRow }
  }
}
