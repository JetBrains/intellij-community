package org.intellij.plugins.markdown.model.psi.labels

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor
import org.intellij.plugins.markdown.model.psi.MarkdownPsiSymbolReference

internal class UnresolvedLinkLabelInspection: LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object: MarkdownElementVisitor() {
      override fun visitElement(element: PsiElement) {
        checkReference(element, holder)
        super.visitElement(element)
      }
    }
  }

  private fun checkReference(element: PsiElement, holder: ProblemsHolder) {
    val references = MarkdownPsiSymbolReference.findSymbolReferences(element).filterIsInstance<LinkLabelSymbolReference>()
    val unresolvedReferences = references.filter { it.resolveReference().isEmpty() }
    for (reference in unresolvedReferences) {
      val text = reference.rangeInElement.substring(reference.element.text)
      holder.registerProblem(
        element,
        MarkdownBundle.message("markdown.unresolved.link.label.inspection.text", text),
        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
        reference.rangeInElement
      )
    }
  }
}
