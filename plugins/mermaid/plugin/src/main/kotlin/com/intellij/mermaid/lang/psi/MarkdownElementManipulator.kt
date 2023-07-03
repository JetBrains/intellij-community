package com.intellij.mermaid.lang.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator

class MarkdownElementManipulator : AbstractElementManipulator<MermaidLanguageInjectionHost>() {
  override fun handleContentChange(
    element: MermaidLanguageInjectionHost,
    range: TextRange,
    newContent: String?
  ): MermaidLanguageInjectionHost {
    val oldText = element.text
    val newText = oldText.substring(0, range.startOffset) + newContent + oldText.substring(range.endOffset)
    val type = element.containingFile.fileType
    val newElement = MermaidElementFactory.createMarkdownValue(element.project, newText)
      ?: error(type.toString() + " " + type.defaultExtension + " " + newText)
    return element.replace(newElement) as MermaidLanguageInjectionHost
  }
}
