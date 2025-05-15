package org.intellij.plugins.markdown.lang.references.paths

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceOwner
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElementFactory
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MarkdownLinkDestinationWithSpacesInspection: LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object: MarkdownElementVisitor() {
      override fun visitLinkDestination(linkDestination: MarkdownLinkDestination) {
        checkReference(linkDestination, holder)
      }
    }
  }

  private fun checkReference(element: MarkdownLinkDestination, holder: ProblemsHolder) {
    val references = element.references.filterNotNull().filter { it is FileReferenceOwner }
    if (references.isEmpty()) return
    @Suppress("NAME_SHADOWING")
    val element = references.first().element as? MarkdownLinkDestination ?: return
    val text = element.text
    val ranges = references.map { it.rangeInElement }.filterNot { it.isEmpty || it.endOffset > text.length }
    if (!text.contains(' ') || ranges.none()) return
    val range = TextRange(ranges.first().startOffset, ranges.last().endOffset)
    val replacement = text.substring(range.startOffset, range.endOffset).replace(" ", "%20")
    holder.registerProblem(
      element,
      MarkdownBundle.message("markdown.link.destination.with.spaces.inspection.description"),
      ProblemHighlightType.WARNING,
      range,
      ReplaceSpacesInsideLinkFix(element, range, replacement)
    )
  }

  // range is inside a link element, so should be safe.
  private class ReplaceSpacesInsideLinkFix(
    link: MarkdownLinkDestination,
    @SafeFieldForPreview private val range: TextRange,
    private val replacement: String
  ): LocalQuickFixOnPsiElement(link) {
    override fun getFamilyName(): String {
      return text
    }

    override fun getText(): String {
      return MarkdownBundle.message("markdown.link.destination.with.spaces.quick.fix.name", replacement)
    }

    override fun invoke(project: Project, psiFile: PsiFile, startElement: PsiElement, endElement: PsiElement) {
      require(startElement is MarkdownLinkDestination)
      val content = range.replace(startElement.text, replacement)
      val element = MarkdownPsiElementFactory.createLinkDestination(project, content)
      startElement.replace(element)
    }
  }
}
