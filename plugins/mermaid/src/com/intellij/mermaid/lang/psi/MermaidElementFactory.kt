// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.psi

import com.intellij.mermaid.lang.MermaidLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.parentOfType

class MermaidElementFactory {
  companion object {
    fun createClassDiagramStatement(project: Project, name: String): MermaidClassStatement? {
      val text = """
        classDiagram
          class $name
      """.trimIndent()
      val file = createFile(project, text)
      return file.traverse().filterIsInstance<MermaidClassStatement>().firstOrNull()
    }

    fun createGenericElement(project: Project, name: String): MermaidGeneric? {
      val text = """
        classDiagram
          class A~$name~
      """.trimIndent()
      val file = createFile(project, text)

      return file.findElementAt(text.length - 1)!!.parentOfType()
    }

    fun createBranchStatement(project: Project, name: String): MermaidBranchStatement? {
      val text = """
        gitGraph
          branch $name
      """.trimIndent()
      val file = createFile(project, text)
      return file.traverse().filterIsInstance<MermaidBranchStatement>().firstOrNull()
    }

    fun createCommitStatement(project: Project, name: String): MermaidCommitStatement? {
      val text = """
        gitGraph
          commit id: "$name"
      """.trimIndent()
      val file = createFile(project, text)
      return file.traverse().filterIsInstance<MermaidCommitStatement>().firstOrNull()
    }

    fun createCommitIdValue(project: Project, id: String): MermaidCommitIdValue? {
      val text = """
        gitGraph
          commit id: "$id"
      """.trimIndent()
      val file = createFile(project, text)
      return file.traverse().filterIsInstance<MermaidCommitIdValue>().firstOrNull()
    }

    fun createParentIdAttribute(project: Project, id: String): MermaidParentCommitIdAttribute? {
      val text = """
        gitGraph
          cherry-pick id: "1" parent: "$id"
      """.trimIndent()
      val file = createFile(project, text)
      return file.traverse().filterIsInstance<MermaidParentCommitIdAttribute>().firstOrNull()
    }

    fun createSpaceElement(project: Project, length: Int): PsiElement {
      check(length > 0) { "Length of element should be > 0" }
      val text = """
        flowchart
          ${"&nbsp".repeat(length)}
      """.trimIndent()
      val file = createFile(project, text)

      return checkNotNull(file.findElementAt("flowchart\n  ".length))
    }

    fun createIdElement(project: Project, vararg elements: PsiElement): PsiElement? {
      val text = """
        flowchart
          ${elements.joinToString(separator = "") { it.text }}
      """.trimIndent()
      val file = createFile(project, text)

      return file.findElementAt("flowchart\n  ".length)
    }

    fun createEOL(project: Project): PsiElement {
      val text = "\n"
      val file = createFile(project, text)

      return file.firstChild
    }

    fun createMarkdownValue(project: Project, value: String): MermaidMarkdownValue? {
      val text = buildString {
        appendLine("flowchart")
        append("markdown[\"`")
        append(value)
        appendLine("`\"]")
      }
      val file = createFile(project, text)
      return file.traverse().filterIsInstance<MermaidMarkdownValue>().firstOrNull()
    }

    fun createDirectiveValue(project: Project, value: String): MermaidDirectiveValue? {
      val text = buildString {
        append("%%")
        append(value)
        appendLine("%%")
        appendLine("graph")
      }

      val file = createFile(project, text)
      return file.traverse().filterIsInstance<MermaidDirectiveValue>().firstOrNull()
    }

    fun createFrontmatterContent(project: Project, value: String): MermaidFrontmatterContent? {
      val text = buildString {
        appendLine("---")
        appendLine(value)
        appendLine("---")
        appendLine("graph")
      }

      val file = createFile(project, text)
      return file.traverse().filterIsInstance<MermaidFrontmatterContent>().firstOrNull()
    }

    private fun createFile(project: Project?, text: String): MermaidFile {
      val name = "dummy.mermaid"
      return PsiFileFactory.getInstance(project).createFileFromText(name, MermaidLanguage, text) as MermaidFile
    }
  }
}
