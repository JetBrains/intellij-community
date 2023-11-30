// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.intentions

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.tables.TableFormattingUtils
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.isCorrectlyFormatted
import org.intellij.plugins.markdown.editor.tables.TableUtils

internal class ReformatTableIntention: BaseElementAtCaretIntentionAction() {
  override fun getFamilyName(): String = text

  override fun getText(): String {
    return MarkdownBundle.message("markdown.reformat.table.intention.text")
  }

  override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
    val table = TableUtils.findTable(element)
    if (table == null) {
      return false
    }
    return !table.isCorrectlyFormatted()
  }

  override fun invoke(project: Project, editor: Editor, element: PsiElement) {
    val table = TableUtils.findTable(element)!!
    TableFormattingUtils.reformatAllColumns(table, editor.document, trimToMaxContent = true)
  }
}
