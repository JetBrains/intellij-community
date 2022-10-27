package com.intellij.mermaid.lang.psi

import com.intellij.mermaid.lang.MermaidLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.parentOfType

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

    fun createGenericElement(project: Project, name: String): MermaidGeneric? {
      val text = """
        classDiagram
          class A~$name~
      """.trimIndent()
      val file = createFile(project, text)

      return file.findElementAt(text.length - 1)!!.parentOfType()
    }

    fun createBranchStatement(project: Project, name: String): MermaidGitGraphStatement? {
      val text = """
        gitGraph
          branch $name
      """.trimIndent()
      val file = createFile(project, text)

      val element = file.findElementAt("gitGraph\n  ".length)?.parent?.parent
      return element as? MermaidGitGraphStatement
    }

    fun createCommitStatement(project: Project, name: String): MermaidGitGraphStatement? {
      val text = """
        gitGraph
          commit id: "$name"
      """.trimIndent()
      val file = createFile(project, text)

      val element = file.findElementAt("gitGraph\n  ".length)?.parent?.parent
      return element as? MermaidGitGraphStatement
    }

    fun createSpaceElement(project: Project, length: Int): PsiElement? {
      val text = """
        flowchart
          ${"&nbsp".repeat(length)}
      """.trimIndent()
      val file = createFile(project, text)

      return file.findElementAt("flowchart\n  ".length)
    }

    fun createIdElement(project: Project, vararg elements: PsiElement): PsiElement? {
      val text = """
        flowchart
          ${elements.joinToString(separator = "") { it.text }}
      """.trimIndent()
      val file = createFile(project, text)

      return file.findElementAt("flowchart\n  ".length)
    }

    private fun createFile(project: Project?, text: String): MermaidFile {
      val name = "dummy.mermaid"
      return PsiFileFactory.getInstance(project).createFileFromText(name, MermaidLanguage, text) as MermaidFile
    }
  }
}
