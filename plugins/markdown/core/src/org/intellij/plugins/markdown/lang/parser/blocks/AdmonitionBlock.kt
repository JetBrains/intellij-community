// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.parser.blocks

import org.intellij.markdown.IElementType
import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.markerblocks.MarkerBlock
import org.intellij.markdown.parser.markerblocks.MarkerBlockImpl
import org.intellij.markdown.parser.sequentialparsers.SequentialParser

internal class AdmonitionBlock(
  startPosition: LookaheadText.Position,
  constraints: MarkdownConstraints,
  private val productionHolder: ProductionHolder,
) : MarkerBlockImpl(constraints, productionHolder.mark()) {

  private val titleEndOffset: Int = startPosition.nextLineOrEofOffset
  private var contentStartOffset: Int = -1
  private var contentEndOffset: Int = -1

  init {
    val titleRange = startPosition.offset..titleEndOffset
    productionHolder.addProduction(listOf(
      SequentialParser.Node(titleRange, AdmonitionMarkerProvider.ADMONITION_TITLE)
    ))
  }

  override fun allowsSubBlocks(): Boolean = false

  override fun isInterestingOffset(pos: LookaheadText.Position): Boolean {
    return pos.offsetInCurrentLine == -1
  }

  override fun calcNextInterestingOffset(pos: LookaheadText.Position): Int {
    return pos.nextLineOrEofOffset
  }

  override fun doProcessToken(
    pos: LookaheadText.Position,
    currentConstraints: MarkdownConstraints,
  ): MarkerBlock.ProcessingResult {
    if (!AdmonitionMarkerProvider.isAdmonitionContentLine(pos.currentLine)) {
      return MarkerBlock.ProcessingResult.DEFAULT
    }
    if (contentStartOffset == -1) {
      contentStartOffset = pos.offset
    }
    contentEndOffset = pos.nextLineOrEofOffset
    return MarkerBlock.ProcessingResult.CANCEL
  }

  override fun acceptAction(action: MarkerBlock.ClosingAction): Boolean {
    if ((action == MarkerBlock.ClosingAction.DONE || action == MarkerBlock.ClosingAction.DEFAULT)
        && contentStartOffset != -1
        && contentEndOffset > contentStartOffset) {
      val contentRange = contentStartOffset..contentEndOffset
      productionHolder.addProduction(listOf(
        SequentialParser.Node(contentRange, AdmonitionMarkerProvider.ADMONITION_CONTENT)
      ))
    }
    return super.acceptAction(action)
  }

  override fun getDefaultAction(): MarkerBlock.ClosingAction {
    return MarkerBlock.ClosingAction.DONE
  }

  override fun getDefaultNodeType(): IElementType {
    return AdmonitionMarkerProvider.ADMONITION
  }
}
