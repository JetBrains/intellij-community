package com.intellij.mermaid.lang.intention

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.psi.*
import com.intellij.mermaid.lang.psi.MermaidElementFactory.Companion.createClassDiagramStatement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.siblings


class ClassDiagramAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element is MermaidClassDiagramIdentifierHolder && element !is MermaidClassDiagramIdentifierDeclarationHolder) {
      annotateUnresolvedClass(element, holder)
    }
  }

  private fun annotateUnresolvedClass(element: MermaidClassDiagramIdentifierHolder, holder: AnnotationHolder) {
    val identifier = element.classDiagramIdentifier
    val text = identifier.text
    val parent = element.parent ?: return

    val prevSiblings = parent
      .siblings(forward = false, withSelf = false)
      .filterIsInstance<MermaidClassDiagramStatement>()
      .map { it.firstChild }

    val classStatementIdentifiers = prevSiblings
      .filterIsInstance<MermaidClassStatement>()
      .map { it.classDiagramIdentifier }
    val mermaidStatementIdentifiers = prevSiblings
      .filterIsInstance<MermaidRelationStatement>()
      .flatMap { listOf(it.leftId.classDiagramIdentifier, it.rightId.classDiagramIdentifier) }
    val matchingIds = (classStatementIdentifiers + mermaidStatementIdentifiers)
      .map { it.text }
      .filter { it == text }

    if (matchingIds.toList().isNotEmpty()) {
      return
    }

    holder.newAnnotation(HighlightSeverity.ERROR, MermaidBundle.message("annotator.unresolved.class"))
      .range(identifier.textRange)
      .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
      .withFix(CreateClassDeclarationIntention(identifier.text))
      .create()
  }

  private class CreateClassDeclarationIntention(@SafeFieldForPreview private val className: String) :
    AbstractCreateDeclarationIntention(className) {
    override fun getText(): String = MermaidBundle.message("fix.create.class.declaration", className)
    override fun createDeclarationPsiElement(project: Project, name: String) = createClassDiagramStatement(project, name)
  }
}
