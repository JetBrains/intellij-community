package com.intellij.mermaid.lang.intention

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.psi.*
import com.intellij.mermaid.lang.psi.MermaidElementFactory.Companion.createBranchStatement
import com.intellij.mermaid.lang.psi.MermaidElementFactory.Companion.createCommitStatement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.siblings


class GitGraphAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    when (element) {
      is MermaidMergeStatement -> {
        annotateUnresolvedBranch(element, holder)
        annotateConflictingCommitId(element, holder)
      }

      is MermaidCheckoutStatement -> annotateUnresolvedBranch(element, holder)
      is MermaidCherryPickStatement -> annotateUnresolvedCommitId(element, holder)
      is MermaidCommitStatement -> annotateConflictingCommitId(element, holder)
    }
  }

  private fun annotateUnresolvedBranch(element: PsiElement, holder: AnnotationHolder) {
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
      .withFix(CreateBranchDeclarationIntention(identifier, parent))
      .create()
  }

  private fun annotateUnresolvedCommitId(element: MermaidCherryPickStatement, holder: AnnotationHolder) {
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
      .withFix(CreateCommitDeclarationIntention(identifier, parent))
      .create()
  }

  private fun annotateConflictingCommitId(element: MermaidMergeStatement, holder: AnnotationHolder) {
    val identifier = element.commitIdAttribute?.commitIdValue ?: return

    val text = identifier.text
    val parent = element.parentOfType<MermaidGitGraphDocument>() ?: return

    val siblings = parent
      .children
      .filterIsInstance<MermaidGitGraphStatement>()
      .map { it.firstChild }

    val mergeStatementIdentifiers = siblings
      .filterIsInstance<MermaidMergeStatement>()
      .filter { it != element }
      .mapNotNull { it.commitIdAttribute?.commitIdValue }
    val commitStatementIdentifiers = siblings
      .filterIsInstance<MermaidCommitStatement>()
      .mapNotNull { it.commitIdAttribute?.commitIdValue }
    val matchingIds = (mergeStatementIdentifiers + commitStatementIdentifiers)
      .map { it.text }
      .filter { it == text }

    if (matchingIds.toList().isEmpty()) {
      return
    }

    holder.newAnnotation(HighlightSeverity.ERROR, MermaidBundle.message("annotator.conflicting.commit.id"))
      .range(identifier.textRange)
      .highlightType(ProblemHighlightType.ERROR)
      .create()
  }

  private fun annotateConflictingCommitId(element: MermaidCommitStatement, holder: AnnotationHolder) {
    val identifier = element.commitIdAttribute?.commitIdValue ?: return

    val text = identifier.text
    val parent = element.parentOfType<MermaidGitGraphDocument>() ?: return

    val siblings = parent
      .children
      .filterIsInstance<MermaidGitGraphStatement>()
      .map { it.firstChild }

    val matchingMergeStatementIdentifiers = siblings
      .filterIsInstance<MermaidMergeStatement>()
      .mapNotNull { it.commitIdAttribute?.commitIdValue?.text }
      .filter { it == text }

    if (matchingMergeStatementIdentifiers.isNotEmpty()) {
      holder.newAnnotation(HighlightSeverity.ERROR, MermaidBundle.message("annotator.conflicting.commit.id"))
        .range(identifier.textRange)
        .highlightType(ProblemHighlightType.ERROR)
        .create()

      return
    }

    val matchingCommitStatementIdentifiers = siblings
      .filterIsInstance<MermaidCommitStatement>()
      .filter { it != element }
      .mapNotNull { it.commitIdAttribute?.commitIdValue?.text }
      .filter { it == text }

    if (matchingCommitStatementIdentifiers.toList().isNotEmpty()) {
      holder.newAnnotation(HighlightSeverity.WARNING, MermaidBundle.message("annotator.conflicting.commit.id"))
        .range(identifier.textRange)
        .highlightType(ProblemHighlightType.WARNING)
        .create()

      return
    }
  }

  private class CreateBranchDeclarationIntention(psiElement: PsiElement, statement: PsiElement, private val className: String = psiElement.text) :
    AbstractCreateDeclarationIntention(psiElement, statement, className) {
    override fun getText(): String = MermaidBundle.message("fix.create.branch.declaration", className)

    override fun createDeclarationPsiElement(project: Project, name: String) = createBranchStatement(project, name)
  }

  private class CreateCommitDeclarationIntention(psiElement: PsiElement, statement: PsiElement, private val className: String = psiElement.text) :
    AbstractCreateDeclarationIntention(psiElement, statement, className) {
    override fun getText(): String = MermaidBundle.message("fix.create.commit.declaration", className)

    override fun createDeclarationPsiElement(project: Project, name: String) = createCommitStatement(project, name)
  }
}
