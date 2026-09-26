// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.intention

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction
import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.lexer.MermaidTokenTypeSets.DIAGRAM_BODIES_AND_BLOCKS
import com.intellij.mermaid.lang.lexer.MermaidTokenTypeSets.STATEMENTS
import com.intellij.mermaid.lang.psi.MermaidElementFactory
import com.intellij.mermaid.lang.psi.parentOfType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

abstract class AbstractCreateDeclarationIntention(
  private val className: String,
  private val isQuoted: Boolean = false,
  private val quote: String = "\""
) :
  BaseElementAtCaretIntentionAction() {
  abstract fun createDeclarationPsiElement(project: Project, name: String): PsiElement?

  override fun getFamilyName() = MermaidBundle.message("fix.create.declaration")

  override fun isAvailable(project: Project, editor: Editor, element: PsiElement) = true

  override fun invoke(project: Project, editor: Editor, element: PsiElement) {
    createDeclaration(project, element)
  }

  private fun createDeclaration(project: Project, element: PsiElement) {
    val statement = element.parentOfType(type = STATEMENTS) ?: return
    val document = element.parentOfType(type = DIAGRAM_BODIES_AND_BLOCKS) ?: return

    val name = buildString {
      if (isQuoted) append(quote)
      append(className.replace(" ", "\\\\ "))
      if (isQuoted) append(quote)
    }

    val declaration = createDeclarationPsiElement(project, name)
      ?: return

    document.addBefore(declaration, statement)
    document.addBefore(MermaidElementFactory.createEOL(project), statement)
  }
}
