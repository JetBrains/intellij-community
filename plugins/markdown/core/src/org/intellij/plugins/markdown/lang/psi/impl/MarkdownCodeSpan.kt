// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.lang.ASTFactory
import com.intellij.lang.ASTNode
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.util.IncorrectOperationException
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.jetbrains.annotations.ApiStatus

class MarkdownCodeSpan(node: ASTNode) : MarkdownCompositePsiElementBase(node), PsiExternalReferenceHost {
  override fun getPresentableTagName(): String = "code_span"
  override fun getReferences(): Array<PsiReference> = ReferenceProvidersRegistry.getReferencesFromProviders(this)

  @ApiStatus.Internal
  fun getContentRange(): TextRange? {
    val text = text
    val (openingMarkerLength, closingMarkerLength) = getMarkerLengths(node) ?: return null

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
    override fun handleContentChange(element: MarkdownCodeSpan, range: TextRange, newContent: String): MarkdownCodeSpan {
      if (newContent.startsWith('`') || newContent.endsWith('`')) return element
      return replaceContent(element, newContent) ?: throw IncorrectOperationException("Failed to create code span")
    }

    override fun getRangeInElement(element: MarkdownCodeSpan): TextRange = element.getContentRange() ?: TextRange.EMPTY_RANGE

    private fun replaceContent(element: MarkdownCodeSpan, newContent: String): MarkdownCodeSpan? {
      val (openingMarkerLength, closingMarkerLength) = getMarkerLengths(element.node, newContent) ?: return null
      val node = ASTFactory.composite(MarkdownElementTypes.CODE_SPAN)
      node.rawAddChildren(ASTFactory.leaf(MarkdownTokenTypes.BACKTICK, "`".repeat(openingMarkerLength)))
      node.rawAddChildren(ASTFactory.leaf(MarkdownTokenTypes.TEXT, newContent))
      node.rawAddChildren(ASTFactory.leaf(MarkdownTokenTypes.BACKTICK, "`".repeat(closingMarkerLength)))
      CodeEditUtil.setNodeGeneratedRecursively(node, true)

      val parent = element.node.treeParent ?: return null
      val replacement = MarkdownCodeSpan(node)
      parent.replaceChild(element.node, node)
      return replacement
    }

    private fun getMarkerLengths(node: ASTNode, newContent: String): Pair<Int, Int>? {
      val (openingMarkerLength, closingMarkerLength) = getMarkerLengths(node) ?: return null
      var maxBacktickSequenceLength = 0
      var currentBacktickSequenceLength = 0
      for (character in newContent) {
        if (character == '`') {
          currentBacktickSequenceLength++
          maxBacktickSequenceLength = maxOf(maxBacktickSequenceLength, currentBacktickSequenceLength)
        }
        else {
          currentBacktickSequenceLength = 0
        }
      }
      val requiredMarkerLength = maxBacktickSequenceLength + 1
      return Pair(maxOf(openingMarkerLength, requiredMarkerLength), maxOf(closingMarkerLength, requiredMarkerLength))
    }
  }
}

private fun getMarkerLengths(node: ASTNode): Pair<Int, Int>? {
  val openingMarker = node.firstChildNode ?: return null
  val closingMarker = node.lastChildNode ?: return null
  if (openingMarker == closingMarker ||
      openingMarker.elementType != MarkdownTokenTypes.BACKTICK ||
      closingMarker.elementType != MarkdownTokenTypes.BACKTICK) {
    return null
  }
  return Pair(openingMarker.textLength, closingMarker.textLength)
}
