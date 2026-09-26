// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.intention

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.psi.MermaidClassDiagramIdentifierDeclarationHolder
import com.intellij.mermaid.lang.psi.MermaidClassDiagramIdentifierHolder
import com.intellij.mermaid.lang.psi.MermaidClassStatement
import com.intellij.mermaid.lang.psi.MermaidElementFactory.Companion.createClassDiagramStatement
import com.intellij.mermaid.lang.psi.MermaidRelationStatement
import com.intellij.mermaid.lang.psi.identifier
import com.intellij.mermaid.lang.psi.isQuoted
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
    val identifier = element.identifier()

    val prevSiblings = element.siblings(forward = false, withSelf = false)

    val classStatementIdentifiers = prevSiblings
      .filterIsInstance<MermaidClassStatement>()
      .map { it.classHeader.identifier() }
    val mermaidStatementIdentifiers = prevSiblings
      .filterIsInstance<MermaidRelationStatement>()
      .flatMap { listOf(it.leftId.identifier(), it.rightId.identifier()) }
    val matchingIds = (classStatementIdentifiers + mermaidStatementIdentifiers)
      .filter { identifier.textMatches(it) }

    if (matchingIds.toList().isNotEmpty()) {
      return
    }

    holder.newAnnotation(HighlightSeverity.ERROR, MermaidBundle.message("annotator.unresolved.class"))
      .range(identifier.textRange)
      .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
      .withFix(CreateClassDeclarationIntention(identifier.text, element.isQuoted()))
      .create()
  }

  private class CreateClassDeclarationIntention(private val className: String, isQuoted: Boolean) :
    AbstractCreateDeclarationIntention(className, isQuoted, "`") {
    override fun getText(): String = MermaidBundle.message("fix.create.class.declaration", className)
    override fun createDeclarationPsiElement(project: Project, name: String) =
      createClassDiagramStatement(project, name)
  }
}
