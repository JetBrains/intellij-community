// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.formatter.blocks.special

import com.intellij.formatting.Alignment
import com.intellij.formatting.Block
import com.intellij.formatting.SpacingBuilder
import com.intellij.formatting.Wrap
import com.intellij.formatting.WrapType
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.formatter.blocks.MarkdownBlocks
import org.intellij.plugins.markdown.lang.formatter.blocks.MarkdownFormattingBlock
import org.intellij.plugins.markdown.lang.formatter.blocks.MarkdownTextUtil.isFormatterWhitespace
import org.intellij.plugins.markdown.lang.formatter.blocks.MarkdownTextUtil.isPunctuation
import org.intellij.plugins.markdown.lang.psi.util.children

/**
 * Markdown special formatting block that puts all it [MarkdownTokenTypes.TEXT] children inside wrap.
 *
 * Allows wrapping paragraphs around right margin. So, it kind of emulates reflow formatting for paragraphs.
 *
 * @param contentAlignment the alignment of the children except the `>` markers
 */
internal open class MarkdownWrappingFormattingBlock(
  settings: CodeStyleSettings,
  spacing: SpacingBuilder,
  node: ASTNode,
  alignment: Alignment? = null,
  wrap: Wrap? = null,
  protected val contentAlignment: Alignment? = alignment,
  quoteMarkerAlignment: Alignment? = null,
) : MarkdownFormattingBlock(node, settings, spacing, alignment, wrap, quoteMarkerAlignment) {
  /** Number of newlines in this block's text */
  val newlines: Int
    get() = node.text.count { it == '\n' }

  override fun buildChildren(): List<Block> {
    val filtered = MarkdownBlocks.filterFromWhitespaces(node.children()).toList()
    val childWrap = createWrapForChildren()
    val noneWrap = Wrap.createWrap(WrapType.NONE, false)
    val result = ArrayList<Block>(filtered.size)

    filtered.forEachIndexed { index, child ->
      val previous = filtered.getOrNull(index - 1)
      val parentType = node.treeParent?.elementType
      val isAfterMarker = index == 0 && (parentType == MarkdownElementTypes.LIST_ITEM || parentType == MarkdownElementTypes.BLOCK_QUOTE)
                          || previous?.elementType == MarkdownTokenTypes.BLOCK_QUOTE
      val isAfterOpeningParenthesis = previous?.elementType == MarkdownTokenTypes.LPAREN
      val isAdjacentToPreviousQuote = previous != null
                                      && (previous.elementType == MarkdownTokenTypes.SINGLE_QUOTE
                                          || previous.elementType == MarkdownTokenTypes.DOUBLE_QUOTE)
                                      && previous.textRange.endOffset == child.textRange.startOffset
      when (child.elementType) {
        MarkdownTokenTypes.LPAREN -> {
          val wrap = if (isAfterMarker) noneWrap else childWrap
          result.add(MarkdownFormattingBlock(child, settings, spacing, contentAlignment, wrap))
        }

        MarkdownTokenTypes.TEXT -> {
          processTextElement(result, child, childWrap,
                             !isAfterMarker && !isAfterOpeningParenthesis && !isAdjacentToPreviousQuote)
        }

        MarkdownTokenTypes.SINGLE_QUOTE, MarkdownTokenTypes.DOUBLE_QUOTE -> {
          val isAttachedToPreviousText = previous?.elementType == MarkdownTokenTypes.TEXT
                                         && previous.textRange.endOffset == child.textRange.startOffset
          val wrap = if (isAfterMarker || isAttachedToPreviousText) noneWrap else childWrap
          result.add(MarkdownFormattingBlock(child, settings, spacing, contentAlignment, wrap))
        }

        in MarkdownTokenTypeSets.WHITE_SPACES -> {
          result.add(MarkdownFormattingBlock(child, settings, spacing, alignment, noneWrap))
        }

        else -> {
          val wrap = if (isAfterMarker || isAfterOpeningParenthesis) noneWrap else null
          result.add(MarkdownBlocks.create(child, settings, spacing, wrap, quoteMarkerAlignment) { contentAlignment })
        }
      }
    }
    return result
  }

  protected fun createWrapForChildren(wrapFirstElement: Boolean = false): Wrap {
    val wrapType = if (MarkdownBlocks.shouldWrapText(node, settings)) WrapType.NORMAL else WrapType.NONE
    return Wrap.createWrap(wrapType, wrapFirstElement)
  }

  protected open fun processTextElement(result: MutableCollection<Block>, node: ASTNode, wrapping: Wrap?, wrapFirstElement: Boolean) {
    val text = node.text
    val shift = node.textRange.startOffset
    val splits = splitTextForWrapping(text)
    val noneWrapping = Wrap.createWrap(WrapType.NONE, false)
    for ((index, split) in splits.withIndex()) {
      // If there is a single split with punctuation character inside,
      // it means that it is surrounded by a non-text elements,
      // whitespaces, or it was at the start or end of the text.
      // In all of those cases the punctuation shouldn't be wrapped at all.
      // (had to check more than one character for '...'-like punctuation)
      val isPunctuation = split.subSequence(text).all { it.isPunctuation() }
      val isOrderedListMarker = split.subSequence(text).isOrderedListMarker()
      val isNonWrappingFirstElement = index == 0 && !wrapFirstElement
      val actualWrapping = when {
        isPunctuation || isOrderedListMarker || isNonWrappingFirstElement -> noneWrapping
        else -> wrapping
      }
      val range = split.shiftRight(shift)
      val block = MarkdownRangedFormattingBlock(node, range, settings, spacing, contentAlignment, actualWrapping)
      result.add(block)
    }
  }

}

private fun CharSequence.isOrderedListMarker(): Boolean {
  return length in 2..10 && (last() == '.' || last() == ')') && dropLast(1).all { it in '0'..'9' }
}

private fun splitTextForWrapping(text: String): Sequence<TextRange> {
  return sequence {
    var start = -1
    var length = -1
    for ((index, char) in text.withIndex()) {
      if (char.isFormatterWhitespace()) {
        if (length > 0) {
          yield(TextRange.from(start, length))
        }
        start = -1
        length = -1
      }
      else {
        if (start == -1) {
          start = index
          length = 0
        }
        length++
      }
    }
    if (length > 0) {
      yield(TextRange.from(start, length))
    }
  }
}
