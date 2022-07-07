package org.intellij.plugins.markdown.editor.toc

import com.intellij.codeInspection.*
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElementVisitor
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
          "This TOC section does not correspond to the actual structure of the document",
          ProblemHighlightType.WARNING,
          range,
          UpdateTocSectionQuickFix(expectedToc, document)
        )
      }
    }
  }

  private class UpdateTocSectionQuickFix(
    private val expectedToc: String,
    private val document: Document
  ): LocalQuickFix {
    override fun getName(): String {
      return MarkdownBundle.message("markdown.outdated.table.of.contents.quick.fix.name")
    }

    override fun getFamilyName(): String {
      return MarkdownBundle.message("markdown.inspection.group.ruby.name")
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val range = descriptor.textRangeInElement
      executeCommand(project) {
        document.replaceString(range.startOffset, range.endOffset, expectedToc)
      }
    }
  }
}
