package org.intellij.plugins.markdown.model.psi.headers

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination
import org.intellij.plugins.markdown.model.psi.MarkdownPsiSymbolReference
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class UnresolvedHeaderReferenceInspection: LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object: MarkdownElementVisitor() {
      override fun visitLinkDestination(linkDestination: MarkdownLinkDestination) {
        checkReference(linkDestination, holder)
      }
    }
  }

  private fun checkReference(element: PsiElement, holder: ProblemsHolder) {
    val references = MarkdownPsiSymbolReference.findSymbolReferences(element)
    val targetReferences = references.filterIsInstance<HeaderAnchorLinkDestinationReference>()
    val unresolvedReferences = targetReferences.filter { it.resolveReference().isEmpty() }
    for (reference in unresolvedReferences) {
      val text = reference.rangeInElement.substring(reference.element.text)
      holder.registerProblem(
        element,
        MarkdownBundle.message("markdown.unresolved.header.reference.inspection.text", text),
        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
        reference.rangeInElement
      )
    }
  }
}
