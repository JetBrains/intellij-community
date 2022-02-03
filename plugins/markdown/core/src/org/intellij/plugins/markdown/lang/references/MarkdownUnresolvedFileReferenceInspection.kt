// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.references

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiReference
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination

class MarkdownUnresolvedFileReferenceInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : MarkdownElementVisitor() {
      override fun visitLinkDestination(linkDestination: MarkdownLinkDestination) {
        checkReference(linkDestination, holder)
      }
    }
  }

  private fun checkReference(element: PsiElement, holder: ProblemsHolder) {
    element.references.asSequence()
      .filter { !shouldSkip(it) && isValidRange(it) && it.resolve() == null }
      .forEach { holder.registerProblem(it, ProblemsHolder.unresolvedReferenceMessage(it), ProblemHighlightType.LIKE_UNKNOWN_SYMBOL) }
  }

  private fun isValidRange(reference: PsiReference): Boolean {
    val elementRange = reference.element.textRange
    return reference.rangeInElement.endOffset <= elementRange.endOffset - elementRange.startOffset
  }

  // since we resolve any username and any repository github wiki reference, even if the file is not present in this repository,
  // the link may still refer to an existing file, so there must not be a warning.
  // see org.intellij.plugins.markdown.lang.references.MarkdownReferenceProvider.GithubWikiLocalFileReferenceProvider.LINK_PATTERN
  private fun shouldSkip(reference: PsiReference): Boolean {
    return reference is MarkdownReferenceProvider.GithubWikiLocalFileReferenceProvider.GithubWikiLocalFileReferenceWrapper
  }
}
