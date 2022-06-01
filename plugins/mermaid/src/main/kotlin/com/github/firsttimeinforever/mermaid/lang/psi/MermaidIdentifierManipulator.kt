package com.github.firsttimeinforever.mermaid.lang.psi

import com.github.firsttimeinforever.mermaid.lang.MermaidLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.PsiFileFactory
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset

class MermaidIdentifierManipulator : AbstractElementManipulator<MermaidNamedElement>() {
  override fun handleContentChange(
    element: MermaidNamedElement,
    range: TextRange,
    newContent: String?
  ): MermaidNamedElement {
    val oldText = element.containingFile.text
    val newText = oldText.replaceRange(element.startOffset, element.endOffset, newContent ?: "")

    val file = createFile(element.project, newText)
    val identifier: MermaidNamedElement? = file.findElementAt(element.startOffset)?.parent as? MermaidNamedElement?

    identifier?.let { element.replace(it) }
    return element
  }

  private fun createFile(project: Project?, text: String): MermaidFile {
    val name = "dummy.mermaid"
    return PsiFileFactory.getInstance(project).createFileFromText(name, MermaidLanguage, text) as MermaidFile
  }
}
