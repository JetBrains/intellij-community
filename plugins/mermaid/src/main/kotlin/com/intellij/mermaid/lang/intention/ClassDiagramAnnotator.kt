package com.intellij.mermaid.lang.intention

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.psi.MermaidClassDiagramStatement
import com.intellij.mermaid.lang.psi.MermaidClassStatement
import com.intellij.mermaid.lang.psi.MermaidElementFactory.Companion.createClassDiagramStatement
import com.intellij.mermaid.lang.psi.MermaidMemberStatement
import com.intellij.mermaid.lang.psi.MermaidRelationStatement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.siblings
import kotlin.reflect.KFunction2


class ClassDiagramAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element !is MermaidMemberStatement) {
      return
    }

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
      .withFix(CreateClassDeclarationIntention(identifier, parent))
      .create()
  }

  private class CreateClassDeclarationIntention(psiElement: PsiElement, statement: PsiElement, private val className: String = psiElement.text) :
    AbstractCreateDeclarationIntention(psiElement, statement, className) {
    override fun getText(): String = MermaidBundle.message("fix.create.class.declaration", className)
    override val createDeclarationPsiElement: KFunction2<Project, String, PsiElement?>
      get() = ::createClassDiagramStatement
  }
}
