package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.impl.source.tree.LeafPsiElement

internal open class LeafElementManipulator<ElementType: LeafPsiElement>: AbstractElementManipulator<ElementType>() {
  override fun handleContentChange(element: ElementType, range: TextRange, newContent: String): ElementType? {
    val text = element.text
    val content = text.replaceRange(range.startOffset, range.endOffset, newContent)
    return element.replaceWithText(content) as? ElementType
  }
}
