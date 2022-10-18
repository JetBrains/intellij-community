package com.intellij.mermaid.lang.psi

import com.intellij.mermaid.lang.MermaidLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory

class MermaidElementFactory {
  companion object {
    fun createClassDiagramStatement(project: Project, name: String): MermaidClassDiagramStatement? {
      val text = """
        classDiagram
          class $name
      """.trimIndent()
      val file = createFile(project, text)

      val element = file.findElementAt("classDiagram\n  ".length)?.parent?.parent
      return element as? MermaidClassDiagramStatement
    }

    fun createGitGraphStatement(project: Project, name: String): MermaidGitGraphStatement? {
      val text = """
        gitGraph
          branch $name
      """.trimIndent()
      val file = createFile(project, text)

      val element = file.findElementAt("gitGraph\n  ".length)?.parent?.parent
      return element as? MermaidGitGraphStatement
    }

    private fun createFile(project: Project?, text: String): MermaidFile {
      val name = "dummy.mermaid"
      return PsiFileFactory.getInstance(project).createFileFromText(name, MermaidLanguage, text) as MermaidFile
    }
  }
}
