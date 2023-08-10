package org.intellij.plugins.markdown.editor.toc

import com.intellij.codeInspection.*
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

internal class OutdatedTableOfContentsInspection: LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object: MarkdownElementVisitor() {
      override fun visitMarkdownFile(file: MarkdownFile) {
        super.visitMarkdownFile(file)
        checkFile(file, holder)
      }
    }
  }

  private fun checkFile(file: MarkdownFile, holder: ProblemsHolder) {
    val existingRanges = GenerateTableOfContentsAction.findExistingTocs(file).toList()
    if (existingRanges.isEmpty()) {
      return
    }
    val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return
    val expectedToc = GenerateTableOfContentsAction.obtainToc(file)
    for (range in existingRanges.asReversed()) {
      val text = document.getText(range)
      if (text != expectedToc) {
        holder.registerProblem(
          file,
          MarkdownBundle.message("markdown.outdated.table.of.contents.inspection.description"),
          ProblemHighlightType.WARNING,
          range,
          UpdateTocSectionQuickFix(expectedToc)
        )
      }
    }
  }

  private class UpdateTocSectionQuickFix(private val expectedToc: String): LocalQuickFix {
    override fun getName(): String {
      return MarkdownBundle.message("markdown.outdated.table.of.contents.quick.fix.name")
    }

    override fun getFamilyName(): String {
      return MarkdownBundle.message("markdown.inspection.group.ruby.name")
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val file = descriptor.psiElement as? PsiFile
      checkNotNull(file)
      val document = file.viewProvider.document
      if (document == null) {
        thisLogger().error("Failed to find document for the quick fix")
        return
      }
      val range = descriptor.textRangeInElement
      document.replaceString(range.startOffset, range.endOffset, expectedToc)
    }
  }
}
