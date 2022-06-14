// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.references.paths

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceOwner
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination
import org.intellij.plugins.markdown.lang.references.paths.github.GithubWikiLocalFileReference
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MarkdownUnresolvedFileReferenceInspection: LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object: MarkdownElementVisitor() {
      override fun visitLinkDestination(linkDestination: MarkdownLinkDestination) {
        checkReference(linkDestination, holder)
      }
    }
  }

  private fun checkReference(element: PsiElement, holder: ProblemsHolder) {
    val references = element.references.asSequence()
    val fileReferences = references.filter { it is FileReferenceOwner }
    val unresolvedReferences = fileReferences.filter { !shouldSkip(it) && isValidRange(it) && it.resolve() == null }
    for (reference in unresolvedReferences) {
      holder.registerProblem(
        reference,
        ProblemsHolder.unresolvedReferenceMessage(reference),
        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
      )
    }
  }

  private fun isValidRange(reference: PsiReference): Boolean {
    val elementRange = reference.element.textRange
    return reference.rangeInElement.endOffset <= elementRange.endOffset - elementRange.startOffset
  }

  /**
   * Since we resolve any username and any repository github wiki reference,
   * even if the file is not present in this repository,
   * the link may still refer to an existing file, so there must not be a warning.
   *
   * See [GithubWikiLocalFileReferenceProvider.linkPattern].
   */
  private fun shouldSkip(reference: PsiReference): Boolean {
    return reference is GithubWikiLocalFileReference
  }
}
