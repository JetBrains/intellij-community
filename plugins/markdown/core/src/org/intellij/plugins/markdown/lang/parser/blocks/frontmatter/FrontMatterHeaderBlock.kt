package org.intellij.plugins.markdown.lang.parser.blocks.frontmatter

import org.intellij.markdown.IElementType
import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.markerblocks.MarkerBlock
import org.intellij.markdown.parser.markerblocks.MarkerBlockImpl
import org.intellij.markdown.parser.sequentialparsers.SequentialParser

internal class FrontMatterHeaderBlock(
  private val startPosition: LookaheadText.Position,
  constraints: MarkdownConstraints,
  private val productionHolder: ProductionHolder,
  private val openingDelimiterText: String
): MarkerBlockImpl(constraints, productionHolder.mark()) {
  private var lastContentPosition: LookaheadText.Position? = null
  private lateinit var closingDelimiterPosition: LookaheadText.Position
  private var shouldClose = false

  init {
    val range = startPosition.offset..startPosition.nextLineOrEofOffset
    val delimiterNode = SequentialParser.Node(range, FrontMatterHeaderMarkerProvider.FRONT_MATTER_HEADER_DELIMITER)
    productionHolder.addProduction(listOf(delimiterNode))
  }

  override fun allowsSubBlocks(): Boolean = false

  override fun calcNextInterestingOffset(pos: LookaheadText.Position): Int {
    return pos.nextLineOrEofOffset
  }

  override fun doProcessToken(position: LookaheadText.Position, currentConstraints: MarkdownConstraints): MarkerBlock.ProcessingResult {
    if (shouldClose) {
      return MarkerBlock.ProcessingResult.DEFAULT
    }
    if (!isDelimiterLine(position.currentLine)) {
      lastContentPosition = position
      return MarkerBlock.ProcessingResult.CANCEL
    }
    val secondLinePosition = startPosition.nextLinePosition() ?: return MarkerBlock.ProcessingResult.PASS
    val contentStartOffset = secondLinePosition.offset + (secondLinePosition.charsToNonWhitespace() ?: 0)
    if (lastContentPosition == null) {
      return MarkerBlock.ProcessingResult.PASS
    }
    closingDelimiterPosition = position
    val contentRange = contentStartOffset..lastContentPosition!!.nextLineOrEofOffset
    val contentNode = SequentialParser.Node(contentRange, FrontMatterHeaderMarkerProvider.FRONT_MATTER_HEADER_CONTENT)
    val delimiterStartOffset = position.offset + (position.charsToNonWhitespace() ?: 0)
    val closingDelimiterRange = delimiterStartOffset..position.nextLineOrEofOffset
    val delimiterNode = SequentialParser.Node(closingDelimiterRange, FrontMatterHeaderMarkerProvider.FRONT_MATTER_HEADER_DELIMITER)
    productionHolder.addProduction(listOf(contentNode, delimiterNode))
    shouldClose = true
    return MarkerBlock.ProcessingResult.CANCEL
  }

  private fun isDelimiterLine(line: String): Boolean {
    return FrontMatterHeaderMarkerProvider.isDelimiterLine(line) && line == openingDelimiterText
  }

  override fun getDefaultAction(): MarkerBlock.ClosingAction {
    return MarkerBlock.ClosingAction.DONE
  }

  override fun getDefaultNodeType(): IElementType {
    return FrontMatterHeaderMarkerProvider.FRONT_MATTER_HEADER
  }

  override fun isInterestingOffset(pos: LookaheadText.Position): Boolean {
    return pos.offsetInCurrentLine == -1
  }
}
