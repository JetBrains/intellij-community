package org.intellij.plugins.markdown.lang.references

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestinationImpl

class MarkdownUnresolvedFileReferenceInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : MarkdownElementVisitor() {
      override fun visitLinkDestination(linkDestination: MarkdownLinkDestinationImpl) {
        checkReference(linkDestination, holder)
      }
    }
  }

  private fun checkReference(element: PsiElement, holder: ProblemsHolder) {
    element.references
      .filter { it.resolve() == null }
      .forEach { holder.registerProblem(it, ProblemsHolder.unresolvedReferenceMessage(it), ProblemHighlightType.LIKE_UNKNOWN_SYMBOL) }
  }
}
