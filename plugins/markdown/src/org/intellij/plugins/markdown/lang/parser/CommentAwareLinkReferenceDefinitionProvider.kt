// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.parser

import org.intellij.markdown.MarkdownElementTypes
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

class CommentAwareLinkReferenceDefinitionProvider : MarkerBlockProvider<MarkerProcessor.StateInfo> {
  override fun createMarkerBlocks(pos: LookaheadText.Position,
                                  productionHolder: ProductionHolder,
                                  stateInfo: MarkerProcessor.StateInfo): List<MarkerBlock> {

    if (!MarkerBlockProvider.isStartOfLineWithConstraints(pos, stateInfo.currentConstraints)) {
      return emptyList()
    }

    val matchResult = LinkReferenceDefinitionProvider.matchLinkDefinition(pos.textFromPosition) ?: return emptyList()
    for ((i, range) in matchResult.withIndex()) {
      productionHolder.addProduction(listOf(SequentialParser.Node(
        addToRangeAndWiden(range, pos.offset), when (i) {
        0 -> MarkdownElementTypes.LINK_LABEL
        1 -> MarkdownElementTypes.LINK_DESTINATION
        2 ->
          if (pos.currentLineFromPosition.startsWith("[//]: #")) {
            org.intellij.plugins.markdown.lang.MarkdownElementTypes.COMMENT
          }
          else {
            MarkdownElementTypes.LINK_TITLE
          }
        else -> throw AssertionError("There are no more than three groups in this regex")
      })))
    }

    val matchLength = matchResult.last().endInclusive + 1
    val endPosition = pos.nextPosition(matchLength)

    if (endPosition != null && !isEndOfLine(endPosition)) {
      return emptyList()
    }
    return listOf(LinkReferenceDefinitionMarkerBlock(stateInfo.currentConstraints, productionHolder.mark(),
                                                     pos.offset + matchLength))
  }

  override fun interruptsParagraph(pos: LookaheadText.Position, constraints: MarkdownConstraints): Boolean {
    return false
  }
}