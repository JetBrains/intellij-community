package com.intellij.mermaid.lang.intention

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.psi.*
import com.intellij.mermaid.lang.psi.MermaidElementFactory.Companion.createBranchStatement
import com.intellij.mermaid.lang.psi.MermaidElementFactory.Companion.createCommitStatement
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.siblings


class GitGraphAnnotator : Annotator {
  private val MAIN = "main"

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    when (element) {
      is MermaidMergeStatement -> {
        annotateUnresolvedBranch(element, holder)
        annotateConflictingCommitId(element, holder)
      }

      is MermaidCheckoutStatement -> annotateUnresolvedBranch(element, holder)
      is MermaidCherryPickStatement -> annotateUnresolvedCommitId(element, holder)
      is MermaidCommitStatement -> annotateConflictingCommitId(element, holder)
      is MermaidBranchStatement -> annotateConflictingBranch(element, holder)
    }
  }

  private fun annotateUnresolvedBranch(element: PsiElement, holder: AnnotationHolder) {
    val identifier =
      (element as? MermaidMergeStatement)?.identifier
        ?: (element as? MermaidCheckoutStatement)?.identifier
        ?: return

    if (identifier.textMatches(MAIN)) return

    val parent = element.parent ?: return

    val matchingIds = parent
      .siblings(forward = false, withSelf = false)
      .filterIsInstance<MermaidGitGraphStatement>()
      .map { it.firstChild }
      .filterIsInstance<MermaidBranchStatement>()
      .map { it.identifier }
      .filter { identifier.textMatches(it) }

    if (matchingIds.toList().isNotEmpty()) {
      return
    }

    holder.newAnnotation(HighlightSeverity.ERROR, MermaidBundle.message("annotator.unresolved.branch"))
      .range(identifier.textRange)
      .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
      .withFix(CreateBranchDeclarationIntention(identifier.text))
      .create()
  }

  private fun annotateUnresolvedCommitId(element: MermaidCherryPickStatement, holder: AnnotationHolder) {
    val identifier = element.commitIdAttribute.commitIdValue

    val parent = element.parent ?: return

    val matchingIds = parent
      .siblings(forward = false, withSelf = false)
      .filterIsInstance<MermaidGitGraphStatement>()
      .map { it.firstChild }
      .filterIsInstance<MermaidCommitStatement>()
      .mapNotNull { it.commitIdAttribute?.commitIdValue }
      .filter { identifier.textMatches(it) }

    if (matchingIds.toList().isNotEmpty()) {
      return
    }

    holder.newAnnotation(HighlightSeverity.ERROR, MermaidBundle.message("annotator.unresolved.commit.id"))
      .range(identifier.textRange)
      .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
      .withFix(CreateCommitDeclarationIntention(identifier.text))
      .create()
  }

  private fun annotateConflictingCommitId(element: MermaidMergeStatement, holder: AnnotationHolder) {
    val identifier = element.commitIdAttribute?.commitIdValue ?: return

    val parent = element.parentOfType<MermaidGitGraphDocument>() ?: return

    val siblings = parent
      .children()
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
      .filter { identifier.textMatches(it) }

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

    val parent = element.parentOfType<MermaidGitGraphDocument>() ?: return

    val siblings = parent
      .children()
      .filterIsInstance<MermaidGitGraphStatement>()
      .map { it.firstChild }

    val matchingMergeStatementIdentifiers = siblings
      .filterIsInstance<MermaidMergeStatement>()
      .mapNotNull { it.commitIdAttribute?.commitIdValue }
      .filter { identifier.textMatches(it) }

    if (matchingMergeStatementIdentifiers.none()) {
      holder.newAnnotation(HighlightSeverity.ERROR, MermaidBundle.message("annotator.conflicting.commit.id"))
        .range(identifier.textRange)
        .highlightType(ProblemHighlightType.ERROR)
        .create()

      return
    }

    val matchingCommitStatementIdentifiers = siblings
      .filterIsInstance<MermaidCommitStatement>()
      .filter { it != element }
      .mapNotNull { it.commitIdAttribute?.commitIdValue }
      .filter { identifier.textMatches(it) }

    if (matchingCommitStatementIdentifiers.toList().isNotEmpty()) {
      holder.newAnnotation(HighlightSeverity.WARNING, MermaidBundle.message("annotator.conflicting.commit.id"))
        .range(identifier.textRange)
        .highlightType(ProblemHighlightType.WARNING)
        .create()

      return
    }
  }

  private fun annotateConflictingBranch(element: MermaidBranchStatement, holder: AnnotationHolder) {
    val identifier = element.identifier

    if (identifier.textMatches(MAIN)) {
      addConflictingBranchAnnotation(holder, identifier.textRange)
    }

    val parent = element.parentOfType<MermaidGitGraphDocument>() ?: return

    val siblings = parent
      .children()
      .filterIsInstance<MermaidGitGraphStatement>()
      .map { it.firstChild }

    val matchingIds = siblings
      .filterIsInstance<MermaidBranchStatement>()
      .map { it.identifier }
      .filter { identifier.textMatches(it) }

    if (matchingIds.none()) {
      return
    }

    addConflictingBranchAnnotation(holder, identifier.textRange)
  }

  private fun addConflictingBranchAnnotation(holder: AnnotationHolder, textRange: TextRange) {
    holder.newAnnotation(HighlightSeverity.ERROR, MermaidBundle.message("annotator.conflicting.branch"))
      .range(textRange)
      .highlightType(ProblemHighlightType.ERROR)
      .create()
  }

  private class CreateBranchDeclarationIntention(@SafeFieldForPreview private val className: String) :
    AbstractCreateDeclarationIntention(className) {
    override fun getText(): String = MermaidBundle.message("fix.create.branch.declaration", className)

    override fun createDeclarationPsiElement(project: Project, name: String) = createBranchStatement(project, name)
  }

  private class CreateCommitDeclarationIntention(@SafeFieldForPreview private val className: String) :
    AbstractCreateDeclarationIntention(className) {
    override fun getText(): String = MermaidBundle.message("fix.create.commit.declaration", className)

    override fun createDeclarationPsiElement(project: Project, name: String) = createCommitStatement(project, name)
  }
}
