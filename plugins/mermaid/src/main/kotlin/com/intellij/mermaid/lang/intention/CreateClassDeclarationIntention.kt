package com.intellij.mermaid.lang.intention

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.lang.ASTFactory
import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.psi.MermaidClassDiagramStatement
import com.intellij.mermaid.lang.psi.MermaidElementFactory
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType

class CreateClassDeclarationIntention(
  private val psiElement: PsiElement,
  private val className: String = psiElement.text
) : BaseIntentionAction() {
  override fun getText(): String = MermaidBundle.message("fix.create.class.declaration", className)

  override fun getFamilyName() = MermaidBundle.message("fix.create.declaration")

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    invokeLater {
      createDeclaration(project)
    }
  }

  private fun createDeclaration(project: Project) {
    WriteCommandAction.runWriteCommandAction(project) {
      val statement = psiElement.parentOfType<MermaidClassDiagramStatement>() ?: return@runWriteCommandAction
      val parent = statement.parent
      val declaration = MermaidElementFactory.createClassDiagramStatement(project, className.replace(" ", "\\\\ "))
        ?: return@runWriteCommandAction

      parent.node.addChild(declaration.node, statement.node)
      parent.node.addChild(ASTFactory.whitespace("\n"), statement.node)
      (psiElement.lastChild.navigationElement as? Navigatable)?.navigate(true)
    }
  }
}
