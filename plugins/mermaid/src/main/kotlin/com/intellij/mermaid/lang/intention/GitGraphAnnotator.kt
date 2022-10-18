package com.intellij.mermaid.lang.intention

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.psi.*
import com.intellij.psi.PsiElement
import com.intellij.psi.util.siblings


class GitGraphAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element is MermaidMergeStatement || element is MermaidCheckoutStatement) {
      val identifier =
        (element as? MermaidMergeStatement)?.identifier
          ?: (element as? MermaidCheckoutStatement)?.identifier
          ?: return

      val text = identifier.text
      val parent = element.parent ?: return

      val matchingIds = parent
        .siblings(forward = false, withSelf = false)
        .filterIsInstance<MermaidGitGraphStatement>()
        .map { it.firstChild }
        .filterIsInstance<MermaidBranchStatement>()
        .map { it.identifier.text }
        .filter { it == text }

      if (matchingIds.toList().isNotEmpty()) {
        return
      }

      holder.newAnnotation(HighlightSeverity.ERROR, MermaidBundle.message("annotator.unresolved.branch"))
        .range(identifier.textRange)
        .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
        .withFix(CreateBranchDeclarationIntention(identifier))
        .create()
    }
    if (element is MermaidCherryPickStatement) {
      val identifier = element.commitIdAttribute.commitIdValue

      val text = identifier.text
      val parent = element.parent ?: return

      val matchingIds = parent
        .siblings(forward = false, withSelf = false)
        .filterIsInstance<MermaidGitGraphStatement>()
        .map { it.firstChild }
        .filterIsInstance<MermaidCommitStatement>()
        .mapNotNull { it.commitIdAttribute?.commitIdValue?.text }
        .filter { it == text }

      if (matchingIds.toList().isNotEmpty()) {
        return
      }

      holder.newAnnotation(HighlightSeverity.ERROR, MermaidBundle.message("annotator.unresolved.commit.id"))
        .range(identifier.textRange)
        .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
        .withFix(CreateCommitDeclarationIntention(identifier))
        .create()
    }
  }
}
