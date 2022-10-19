package com.intellij.mermaid.lang.intention

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.lang.ASTFactory
import com.intellij.mermaid.MermaidBundle
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

abstract class AbstractCreateDeclarationIntention(
  private val psiElement: PsiElement,
  private val statement: PsiElement,
  private val className: String = psiElement.text
) : BaseIntentionAction() {
  abstract fun createDeclarationPsiElement(project: Project, name: String): PsiElement?

  override fun getFamilyName() = MermaidBundle.message("fix.create.declaration")

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    invokeLater {
      createDeclaration(project)
    }
  }

  private fun createDeclaration(project: Project) {
    WriteCommandAction.runWriteCommandAction(project) {
      val parent = statement.parent
      val declaration = createDeclarationPsiElement(project, className.replace(" ", "\\\\ "))
        ?: return@runWriteCommandAction

      parent.node.addChild(declaration.node, statement.node)
      parent.node.addChild(ASTFactory.whitespace("\n"), statement.node)
      (psiElement.lastChild.navigationElement as? Navigatable)?.navigate(true)
    }
  }
}
