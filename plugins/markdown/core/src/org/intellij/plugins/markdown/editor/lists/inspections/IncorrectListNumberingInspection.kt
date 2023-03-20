package org.intellij.plugins.markdown.editor.lists.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.lists.ListRenumberUtils.obtainMarkerNumber
import org.intellij.plugins.markdown.editor.lists.ListRenumberUtils.renumberInBulk
import org.intellij.plugins.markdown.editor.lists.ListUtils.items
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownList
import org.intellij.plugins.markdown.lang.psi.util.hasType
import org.jetbrains.annotations.ApiStatus

/**
 * Items in ordered lists are expected to have straight numeration starting from 1.
 */
@ApiStatus.Internal
class IncorrectListNumberingInspection: LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object: MarkdownElementVisitor() {
      override fun visitList(list: MarkdownList) {
        super.visitList(list)
        if (!list.hasType(MarkdownElementTypes.ORDERED_LIST)) {
          return
        }
        val quickFix by lazy { ListNumberingFix(list) }
        for ((index, item) in list.items.withIndex()) {
          val actualNumber = item.obtainMarkerNumber() ?: continue
          val expectedNumber = index + 1
          if (expectedNumber != actualNumber) {
            val markerElement = item.markerElement!!
            holder.registerProblem(
              markerElement,
              MarkdownBundle.message("markdown.incorrectly.numbered.list.item.inspection.text", expectedNumber, actualNumber),
              ProblemHighlightType.WEAK_WARNING,
              quickFix
            )
          }
        }
      }
    }
  }

  private class ListNumberingFix(list: MarkdownList): LocalQuickFixOnPsiElement(list) {
    override fun getFamilyName(): String {
      return text
    }

    override fun getText(): String {
      return MarkdownBundle.message("markdown.fix.list.items.numbering,quick.fix.text")
    }

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
      require(startElement is MarkdownList)
      val document = file.viewProvider.document
      if (document == null) {
        thisLogger().error("Failed to find document for the quick fix")
        return
      }
      startElement.renumberInBulk(document, recursive = false, restart = true, inWriteAction = false)
    }
  }
}
