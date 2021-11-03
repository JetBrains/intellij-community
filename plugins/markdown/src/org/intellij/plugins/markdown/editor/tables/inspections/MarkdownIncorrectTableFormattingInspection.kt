// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.hasValidAlignment
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.isCorrectlyFormatted
import org.intellij.plugins.markdown.editor.tables.intentions.FixCellAlignmentIntention
import org.intellij.plugins.markdown.editor.tables.intentions.ReformatTableIntention
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableCellImpl
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableImpl
import org.intellij.plugins.markdown.settings.MarkdownSettings

internal class MarkdownIncorrectTableFormattingInspection: LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!MarkdownSettings.getInstance(holder.project).isEnhancedEditingEnabled) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    return object: MarkdownElementVisitor() {
      override fun visitTable(table: MarkdownTableImpl) {
        super.visitTable(table)
        if (!table.isCorrectlyFormatted(checkAlignment = false)) {
          holder.registerProblem(
            table,
            MarkdownBundle.message("markdown.incorrect.table.formatting.inspection.description"),
            IntentionWrapper(ReformatTableIntention())
          )
        }
      }

      override fun visitElement(element: PsiElement) {
        super.visitElement(element)
        if ((element as? MarkdownTableCellImpl)?.hasValidAlignment() == false) {
          holder.registerProblem(
            element,
            MarkdownBundle.message("markdown.incorrect.table.formatting.inspection.local.cell.description"),
            IntentionWrapper(FixCellAlignmentIntention())
          )
        }
      }
    }
  }
}
