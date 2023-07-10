package com.intellij.mermaid.lang.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator

class MarkdownElementManipulator : AbstractElementManipulator<MermaidMarkdownValue>() {
  override fun handleContentChange(
    element: MermaidMarkdownValue,
    range: TextRange,
    newContent: String?
  ): MermaidMarkdownValue {
    val oldText = element.text
    val newText = oldText.substring(0, range.startOffset) + newContent + oldText.substring(range.endOffset)
    val newElement = checkNotNull(MermaidElementFactory.createMarkdownValue(element.project, newText))
    return element.replace(newElement) as MermaidMarkdownValue
  }
}
