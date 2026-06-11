// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.ApiStatus

class MarkdownCodeSpan(node: ASTNode) : MarkdownCompositePsiElementBase(node), PsiExternalReferenceHost {
  override fun getPresentableTagName(): String = "code_span"
  override fun getReferences(): Array<PsiReference> = ReferenceProvidersRegistry.getReferencesFromProviders(this)

  @ApiStatus.Internal
  fun getContentRange(): TextRange? {
    val text = text
    val openingMarkerLength = text.takeWhile { it == '`' }.length
    val closingMarkerLength = text.takeLastWhile { it == '`' }.length
    if (openingMarkerLength == 0 || closingMarkerLength == 0 || openingMarkerLength + closingMarkerLength >= text.length) {
      return null
    }

    var startOffset = openingMarkerLength
    var endOffset = text.length - closingMarkerLength
    while (startOffset < endOffset && text[startOffset].isWhitespace()) {
      startOffset++
    }
    while (endOffset > startOffset && text[endOffset - 1].isWhitespace()) {
      endOffset--
    }
    if (startOffset >= endOffset) {
      return null
    }

    return TextRange(startOffset, endOffset)
  }

  internal class Manipulator : AbstractElementManipulator<MarkdownCodeSpan>() {
    @Throws(IncorrectOperationException::class)
    override fun handleContentChange(element: MarkdownCodeSpan, range: TextRange, newContent: String): MarkdownCodeSpan =
      replaceContentLeaf(element, range, newContent) ?: throw IncorrectOperationException("Failed to create code span")

    override fun getRangeInElement(element: MarkdownCodeSpan): TextRange = element.getContentRange() ?: TextRange.EMPTY_RANGE

    private fun replaceContentLeaf(element: MarkdownCodeSpan, range: TextRange, newContent: String): MarkdownCodeSpan? {
      val leaf = element.node.findLeafElementAt(range.startOffset)?.psi as? LeafPsiElement ?: return null
      val leafRange = leaf.textRangeInParent
      if (range.startOffset < leafRange.startOffset || range.endOffset > leafRange.endOffset) {
        return null
      }

      val leafChangeRange = range.shiftLeft(leafRange.startOffset)
      leaf.replaceWithText(leafChangeRange.replace(leaf.text, newContent.removeSurrounding("`")))
      return element
    }
  }
}
