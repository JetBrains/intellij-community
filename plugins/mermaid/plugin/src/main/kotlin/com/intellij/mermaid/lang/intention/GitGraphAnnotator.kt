package com.intellij.mermaid.lang.intention

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.psi.*
import com.intellij.mermaid.lang.psi.MermaidElementFactory.Companion.createBranchStatement
import com.intellij.mermaid.lang.psi.MermaidElementFactory.Companion.createCommitStatement
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parents
import com.intellij.psi.util.siblings
import com.intellij.refactoring.suggested.startOffset


class GitGraphAnnotator : Annotator {
  private var mainBranchName = "main"

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    when (element) {
      is MermaidDirectiveValue -> {
        val injectedLanguageManager = InjectedLanguageManager.getInstance(element.project)
        val injectedElement =
          injectedLanguageManager.findInjectedElementAt(element.containingFile, element.startOffset) ?: return
        val directiveObject =
          injectedElement.parents(withSelf = true).filterIsInstance<JsonObject>().firstOrNull() ?: return
        collectMainBranchName(directiveObject)
      }

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

  private fun collectMainBranchName(directive: JsonObject) {
    SyntaxTraverser.psiTraverser(directive)
      .asSequence()
      .filterIsInstance<JsonProperty>()
      .filter { it.name == "mainBranchName" }
      .map { it.value }
      .filterIsInstance<JsonStringLiteral>()
      .lastOrNull()
      ?.let { mainBranchName = it.value }
  }

  private fun annotateUnresolvedBranch(element: MermaidGitGraphBranchIdentifierHolder, holder: AnnotationHolder) {
    val identifier = element.identifier()

    if (identifier.textMatches(mainBranchName)) return

    val parent = element.parent ?: return

    val matchingIds = parent
      .siblings(forward = false, withSelf = false)
      .filterIsInstance<MermaidBranchStatement>()
      .map { it.identifier() }
      .filter { identifier.textMatches(it) }

    if (matchingIds.toList().isNotEmpty()) {
      return
    }

    holder.newAnnotation(HighlightSeverity.ERROR, MermaidBundle.message("annotator.unresolved.branch"))
      .range(identifier.textRange)
      .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
      .withFix(CreateBranchDeclarationIntention(identifier.text, element.isQuoted()))
      .create()
  }

  private fun annotateUnresolvedCommitId(element: MermaidCherryPickStatement, holder: AnnotationHolder) {
    val identifier = element.commitIdAttribute.commitIdValue

    val parent = element.parent ?: return

    val matchingIds = parent
      .siblings(forward = false, withSelf = false)
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

    val parent = element.parentOfType<MermaidGitGraphBody>() ?: return

    val siblings = parent.children()

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

    val parent = element.parentOfType<MermaidGitGraphBody>() ?: return

    val siblings = parent.children()

    val matchingMergeStatementIdentifiers = siblings
      .filterIsInstance<MermaidMergeStatement>()
      .mapNotNull { it.commitIdAttribute?.commitIdValue }
      .filter { identifier.textMatches(it) }

    if (matchingMergeStatementIdentifiers.any()) {
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
    val identifier = element.identifier()

    if (identifier.textMatches(mainBranchName)) {
      addConflictingBranchAnnotation(holder, identifier.textRange)
    }

    val parent = element.parentOfType<MermaidGitGraphBody>() ?: return

    val siblings = parent.children()

    val matchingIds = siblings
      .filterIsInstance<MermaidBranchStatement>()
      .filter { it != element }
      .map { it.identifier() }
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

  private class CreateBranchDeclarationIntention(
    @SafeFieldForPreview private val className: String,
    isQuoted: Boolean
  ) :
    AbstractCreateDeclarationIntention(className, isQuoted) {
    override fun getText(): String = MermaidBundle.message("fix.create.branch.declaration", className)

    override fun createDeclarationPsiElement(project: Project, name: String) = createBranchStatement(project, name)
  }

  private class CreateCommitDeclarationIntention(@SafeFieldForPreview private val className: String) :
    AbstractCreateDeclarationIntention(className) {
    override fun getText(): String = MermaidBundle.message("fix.create.commit.declaration", className)

    override fun createDeclarationPsiElement(project: Project, name: String) = createCommitStatement(project, name)
  }
}
