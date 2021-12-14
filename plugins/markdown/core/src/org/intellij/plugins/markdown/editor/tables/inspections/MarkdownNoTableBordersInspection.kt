// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.hasCorrectBorders
import org.intellij.plugins.markdown.editor.tables.intentions.FixTableBordersIntention
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MarkdownNoTableBordersInspection: LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!MarkdownSettings.getInstance(holder.project).isEnhancedEditingEnabled) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    return object: MarkdownElementVisitor() {
      override fun visitTable(table: MarkdownTable) {
        super.visitTable(table)
        if (!table.hasCorrectBorders()) {
          holder.registerProblem(
            table,
            MarkdownBundle.message("markdown.no.table.borders.inspection.description"),
            IntentionWrapper(FixTableBordersIntention())
          )
        }
      }
    }
  }
}
