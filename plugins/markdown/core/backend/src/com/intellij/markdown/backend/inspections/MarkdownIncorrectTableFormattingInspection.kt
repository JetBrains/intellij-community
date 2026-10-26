// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.markdown.backend.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.tables.TableFormattingUtils
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.hasValidAlignment
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.isCorrectlyFormatted
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.updateAlignment
import org.intellij.plugins.markdown.editor.tables.TableUtils.findCell
import org.intellij.plugins.markdown.editor.tables.TableUtils.findTable
import org.intellij.plugins.markdown.editor.tables.TableUtils.getColumnAlignment
import org.intellij.plugins.markdown.editor.tables.TableUtils.getTableStyle
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
        val tableStyle = getTableStyle(table.containingFile)
        if (!table.isCorrectlyFormatted(tableStyle, checkAlignment = false)) {
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
        val tableStyle = getTableStyle(cell.containingFile)
        val alignment = table.getColumnAlignment(cell.columnIndex)
        if (alignment != MarkdownTableSeparatorRow.CellAlignment.NONE) {
          if (!cell.hasValidAlignment(tableStyle)) {
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

  private class ReformatTableFix: LocalQuickFix {
    override fun getName(): String = MarkdownBundle.message("markdown.reformat.table.intention.text")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val table = findTable(descriptor.psiElement) ?: return
      val document = table.containingFile.viewProvider.document ?: return
      TableFormattingUtils.reformatAllColumns(table, document, getTableStyle(table.containingFile), trimToMaxContent = true)
    }
  }

  private class FixCellAlignmentFix: LocalQuickFix {
    override fun getName(): String = MarkdownBundle.message("markdown.fix.cell.alignment.intention.text")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val cell = findCell(descriptor.psiElement) ?: return
      val table = cell.parentTable ?: return
      val document = cell.containingFile.viewProvider.document ?: return
      cell.updateAlignment(document, table.getColumnAlignment(cell.columnIndex), getTableStyle(cell.containingFile))
    }
  }
}
