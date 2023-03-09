// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.hasValidAlignment
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.isCorrectlyFormatted
import org.intellij.plugins.markdown.editor.tables.TableUtils.getColumnAlignment
import org.intellij.plugins.markdown.editor.tables.intentions.FixCellAlignmentIntention
import org.intellij.plugins.markdown.editor.tables.intentions.ReformatTableIntention
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableCell
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableSeparatorRow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MarkdownIncorrectTableFormattingInspection: LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object: MarkdownElementVisitor() {
      override fun visitTable(table: MarkdownTable) {
        super.visitTable(table)
        if (!table.isCorrectlyFormatted(checkAlignment = false)) {
          holder.registerProblem(
            table,
            MarkdownBundle.message("markdown.incorrect.table.formatting.inspection.description"),
            ReformatTableFix()
          )
        }
      }

      override fun visitElement(element: PsiElement) {
        super.visitElement(element)
        val cell = element as? MarkdownTableCell ?: return
        val table = cell.parentTable ?: return
        val alignment = table.getColumnAlignment(cell.columnIndex)
        if (alignment != MarkdownTableSeparatorRow.CellAlignment.NONE) {
          if (!cell.hasValidAlignment()) {
            holder.registerProblem(
              element,
              MarkdownBundle.message("markdown.incorrect.table.formatting.inspection.local.cell.description"),
              FixCellAlignmentFix()
            )
          }
        }
      }
    }
  }

  private class ReformatTableFix: IntentionOnPsiElementWrapper(ReformatTableIntention())

  private class FixCellAlignmentFix: IntentionOnPsiElementWrapper(FixCellAlignmentIntention())
}
