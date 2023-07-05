package com.intellij.mermaid.lang.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator

class DirectiveManipulator : AbstractElementManipulator<MermaidDirectiveValue>() {
  override fun handleContentChange(
    element: MermaidDirectiveValue,
    range: TextRange,
    newContent: String?
  ): MermaidDirectiveValue {
    val oldText = element.text
    val newText = oldText.substring(0, range.startOffset) + newContent + oldText.substring(range.endOffset)
    val type = element.containingFile.fileType
    val newElement = MermaidElementFactory.createDirectiveValue(element.project, newText)
      ?: error(type.toString() + " " + type.defaultExtension + " " + newText)
    return element.replace(newElement) as MermaidDirectiveValue
  }
}
