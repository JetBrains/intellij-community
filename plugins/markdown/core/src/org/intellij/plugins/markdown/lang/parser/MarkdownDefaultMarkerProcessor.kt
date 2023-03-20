// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.parser

import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.flavours.commonmark.CommonMarkMarkerProcessor
import org.intellij.markdown.flavours.gfm.GFMConstraints
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.flavours.gfm.table.GitHubTableMarkerProvider
import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.MarkerProcessorFactory
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.constraints.getCharsEaten
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider
import org.intellij.markdown.parser.markerblocks.providers.*
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.plugins.markdown.lang.parser.blocks.CodeFenceMarkerProvider
import org.intellij.plugins.markdown.lang.parser.blocks.CommentAwareLinkReferenceDefinitionProvider
import org.intellij.plugins.markdown.lang.parser.blocks.DefinitionListMarkerProvider
import org.intellij.plugins.markdown.lang.parser.blocks.frontmatter.FrontMatterHeaderMarkerProvider
import kotlin.math.min

open class MarkdownDefaultMarkerProcessor(
  productionHolder: ProductionHolder,
  constraintsBase: MarkdownConstraints
): CommonMarkMarkerProcessor(productionHolder, constraintsBase) {
  override fun populateConstraintsTokens(
    pos: LookaheadText.Position,
    constraints: MarkdownConstraints,
    productionHolder: ProductionHolder
  ) {
    if (constraints !is GFMConstraints || !constraints.hasCheckbox()) {
      super.populateConstraintsTokens(pos, constraints, productionHolder)
      return
    }

    val line = pos.currentLine
    var offset = pos.offsetInCurrentLine
    while (offset < line.length && line[offset] != '[') {
      offset++
    }
    if (offset == line.length) {
      super.populateConstraintsTokens(pos, constraints, productionHolder)
      return
    }

    check(constraints.types.isNotEmpty())
    val type = when (constraints.types.last()) {
      '>' -> MarkdownTokenTypes.BLOCK_QUOTE
      '.', ')' -> MarkdownTokenTypes.LIST_NUMBER
      else -> MarkdownTokenTypes.LIST_BULLET
    }
    val middleOffset = pos.offset - pos.offsetInCurrentLine + offset
    val endOffset = min(pos.offset - pos.offsetInCurrentLine + constraints.getCharsEaten(pos.currentLine), pos.nextLineOrEofOffset)

    productionHolder.addProduction(listOf(
      SequentialParser.Node(pos.offset..middleOffset, type),
      SequentialParser.Node(middleOffset..endOffset, GFMTokenTypes.CHECK_BOX)
    ))
  }

  override fun getMarkerBlockProviders(): List<MarkerBlockProvider<StateInfo>> {
    return buildList {
      add(CodeBlockProvider())
      add(CodeFenceMarkerProvider())
      add(SetextHeaderProvider())
      add(BlockQuoteProvider())
      add(ListMarkerProvider())
      add(HtmlBlockProvider())
      add(DefinitionListMarkerProvider())
      if (FrontMatterHeaderMarkerProvider.isFrontMatterSupportEnabled()) {
        add(FrontMatterHeaderMarkerProvider())
      }
      add(HorizontalRuleProvider())
      add(GitHubTableMarkerProvider())
      add(AtxHeaderProvider())
      add(CommentAwareLinkReferenceDefinitionProvider())
    }
  }

  object Factory: MarkerProcessorFactory {
    override fun createMarkerProcessor(productionHolder: ProductionHolder): MarkerProcessor<*> {
      return MarkdownDefaultMarkerProcessor(productionHolder, GFMConstraints.BASE)
    }
  }
}
