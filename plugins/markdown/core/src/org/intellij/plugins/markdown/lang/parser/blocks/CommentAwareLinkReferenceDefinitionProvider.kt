// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.parser.blocks

import com.intellij.util.text.CharSequenceSubSequence
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.markerblocks.MarkerBlock
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider
import org.intellij.markdown.parser.markerblocks.impl.LinkReferenceDefinitionMarkerBlock
import org.intellij.markdown.parser.markerblocks.providers.LinkReferenceDefinitionProvider
import org.intellij.markdown.parser.markerblocks.providers.LinkReferenceDefinitionProvider.Companion.addToRangeAndWiden
import org.intellij.markdown.parser.markerblocks.providers.LinkReferenceDefinitionProvider.Companion.isEndOfLine
import org.intellij.markdown.parser.sequentialparsers.SequentialParser

class CommentAwareLinkReferenceDefinitionProvider: MarkerBlockProvider<MarkerProcessor.StateInfo> {
  override fun createMarkerBlocks(
    position: LookaheadText.Position,
    productionHolder: ProductionHolder,
    stateInfo: MarkerProcessor.StateInfo
  ): List<MarkerBlock> {
    if (!MarkerBlockProvider.isStartOfLineWithConstraints(position, stateInfo.currentConstraints)) {
      return emptyList()
    }
    val textFromPosition = CharSequenceSubSequence(position.originalText, position.offset, position.originalText.length)
    val matchResult = LinkReferenceDefinitionProvider.matchLinkDefinition(textFromPosition, 0) ?: return emptyList()
    for ((index, range) in matchResult.withIndex()) {
      val type = when (index) {
        0 -> MarkdownElementTypes.LINK_LABEL
        1 -> MarkdownElementTypes.LINK_DESTINATION
        2 -> continue
        else -> error("There are no more than three groups in this regex")
      }
      val node = SequentialParser.Node(addToRangeAndWiden(range, position.offset), type)
      productionHolder.addProduction(listOf(node))
    }
    val matchLength = matchResult.last().last + 1
    val titleRange = matchResult.getOrNull(2)
    if (titleRange != null) {
      val nodes = processTitle(position, titleRange)
      productionHolder.addProduction(nodes)
    }
    val endPosition = position.nextPosition(matchLength)
    if (endPosition != null && !isEndOfLine(endPosition)) {
      return emptyList()
    }
    val block = LinkReferenceDefinitionMarkerBlock(
      stateInfo.currentConstraints,
      productionHolder.mark(),
      endPosition = position.offset + matchLength
    )
    return listOf(block)
  }

  private fun processTitle(position: LookaheadText.Position, titleRange: IntRange): List<SequentialParser.Node> {
    if (!position.currentLineFromPosition.startsWith("[//]: #")) {
      val range = addToRangeAndWiden(titleRange, position.offset)
      val node = SequentialParser.Node(range, MarkdownElementTypes.LINK_TITLE)
      return listOf(node)
    }
    val titlePosition = position.nextPosition(titleRange.first) ?: return emptyList()
    val textFromCurrentPosition = position.textFromPosition
    val text = textFromCurrentPosition.subSequence(titleRange)
    if (text.startsWith('(') && text.trimEnd().endsWith(')')) {
      val offset = titlePosition.offset
      val actualTitleLength = text.trimEnd().length
      return listOf(
        SequentialParser.Node(
          range = addToRangeAndWiden(titleRange, position.offset),
          type = MarkdownElementTypes.COMMENT
        ),
        SequentialParser.Node(
          range = IntRange(offset, offset + 1),
          type = MarkdownTokenTypes.LPAREN
        ),
        SequentialParser.Node(
          range = IntRange(offset + 1, offset + actualTitleLength - 1),
          type = MarkdownElementTypes.COMMENT_VALUE
        ),
        SequentialParser.Node(
          range = IntRange(offset + actualTitleLength - 1, offset + actualTitleLength),
          type = MarkdownTokenTypes.RPAREN
        )
      )
    }
    return emptyList()
  }

  override fun interruptsParagraph(pos: LookaheadText.Position, constraints: MarkdownConstraints): Boolean {
    return false
  }
}
