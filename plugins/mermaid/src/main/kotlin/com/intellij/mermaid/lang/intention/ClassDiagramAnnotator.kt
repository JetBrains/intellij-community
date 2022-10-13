package com.intellij.mermaid.lang.intention

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.psi.MermaidClassDiagramStatement
import com.intellij.mermaid.lang.psi.MermaidClassStatement
import com.intellij.mermaid.lang.psi.MermaidMemberStatement
import com.intellij.mermaid.lang.psi.MermaidRelationStatement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.siblings


class ClassDiagramAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element !is MermaidMemberStatement) {
      return
    }

    val identifier = element.classDiagramIdentifier
    val text = identifier.text
    val prevSiblings = element.parent
      .siblings(forward = false, withSelf = false)
      .filterIsInstance(MermaidClassDiagramStatement::class.java)
      .map { it.firstChild }

    val classStatementIdentifiers = prevSiblings
      .filterIsInstance(MermaidClassStatement::class.java)
      .map { it.classDiagramIdentifier }
    val mermaidStatementIdentifiers = prevSiblings
      .filterIsInstance(MermaidRelationStatement::class.java)
      .flatMap { it.classDiagramIdentifierList }
    val matchingIds = (classStatementIdentifiers + mermaidStatementIdentifiers)
      .map { it.text }
      .filter { it == text }

    if (matchingIds.toList().isNotEmpty()) {
      return
    }

    holder.newAnnotation(HighlightSeverity.ERROR, MermaidBundle.message("annotator.unresolved.class"))
      .range(identifier.textRange)
      .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
      .withFix(CreateClassDeclarationIntention(identifier))
      .create();
  }
}
