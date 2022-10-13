package com.intellij.mermaid.lang.intention

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.lang.ASTFactory
import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.MermaidFileType
import com.intellij.mermaid.lang.MermaidLanguage
import com.intellij.mermaid.lang.psi.MermaidClassDiagramStatement
import com.intellij.mermaid.lang.psi.MermaidFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType

class CreateClassDeclarationIntention(
  private val psiElement: PsiElement,
  private val className: String = psiElement.text
) : BaseIntentionAction() {
  override fun getText(): String = MermaidBundle.message("fix.create.class.declaration", className)

  override fun getFamilyName() = MermaidBundle.message("fix.create.declaration")

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    ApplicationManager.getApplication().invokeLater {
      val virtualFiles = FileTypeIndex.getFiles(MermaidFileType, GlobalSearchScope.allScope(project))
      createDeclaration(project, virtualFiles.iterator().next())
    }
  }

  private fun createDeclaration(project: Project, file: VirtualFile) {
    WriteCommandAction.runWriteCommandAction(project) {
      val statement = psiElement.parentOfType<MermaidClassDiagramStatement>()
      if (statement != null) {
        val parent = statement.parent
        createDeclaration(project, className.replace(" ", "\\\\ "))?.let {
          parent.node.addChild(it.node, statement.node)
          parent.node.addChild(ASTFactory.whitespace("\n"), statement.node)
          (psiElement.lastChild.navigationElement as Navigatable).navigate(true)
        }
      }
    }
  }

  private fun createDeclaration(project: Project, name: String): MermaidClassDiagramStatement? {
    val text = """
        classDiagram
          class $name
      """.trimIndent()
    val file = createFile(project, text)

    val element = file.findElementAt("classDiagram\n  ".length)?.parent?.parent
    return element as? MermaidClassDiagramStatement
  }

  private fun createFile(project: Project?, text: String): MermaidFile {
    val name = "dummy.mermaid"
    return PsiFileFactory.getInstance(project).createFileFromText(name, MermaidLanguage, text) as MermaidFile
  }
}
