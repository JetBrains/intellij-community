package org.intellij.plugins.markdown.lang.parser.blocks

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.html.isWhitespace
import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.markerblocks.MarkerBlock
import org.intellij.markdown.parser.markerblocks.MarkerBlockImpl
import org.intellij.markdown.parser.sequentialparsers.SequentialParser

internal class DefinitionListBlock(
  startPosition: LookaheadText.Position,
  constraints: MarkdownConstraints,
  private val productionHolder: ProductionHolder
): MarkerBlockImpl(constraints, productionHolder.mark()) {
  private var lastDefinitionStartPosition: LookaheadText.Position? = null
  private var lastDefinitionNextPosition: LookaheadText.Position? = null

  init {
    val range = startPosition.offset..startPosition.nextLineOrEofOffset
    productionHolder.addWrappedProduction(range, DefinitionListMarkerProvider.TERM)
  }

  override fun allowsSubBlocks(): Boolean = false

  override fun calcNextInterestingOffset(pos: LookaheadText.Position): Int {
    return pos.nextLineOrEofOffset
  }

  override fun doProcessToken(position: LookaheadText.Position, currentConstraints: MarkdownConstraints): MarkerBlock.ProcessingResult {
    if (!DefinitionListMarkerProvider.isDefinitionLine(position.currentLine)) {
      val currentLine = position.currentLine
      // isWhitespace from the parser
      if (currentLine.isEmpty() || currentLine.all(::isWhitespace)) {
        if (lastDefinitionStartPosition != null) {
          addDefinitionProduction(lastDefinitionStartPosition!!, lastDefinitionNextPosition!!)
        }
        lastDefinitionStartPosition = null
        lastDefinitionNextPosition = null
        return MarkerBlock.ProcessingResult.CANCEL
      }
      return when (lastDefinitionStartPosition) {
        null -> MarkerBlock.ProcessingResult.DEFAULT
        else -> {
          lastDefinitionNextPosition = position
          MarkerBlock.ProcessingResult.CANCEL
        }
      }
    }
    if (lastDefinitionStartPosition != null) {
      addDefinitionProduction(lastDefinitionStartPosition!!, lastDefinitionNextPosition!!)
    }
    lastDefinitionStartPosition = position
    lastDefinitionNextPosition = position
    return MarkerBlock.ProcessingResult.CANCEL
  }

  override fun acceptAction(action: MarkerBlock.ClosingAction): Boolean {
    if (action == MarkerBlock.ClosingAction.DONE || action == MarkerBlock.ClosingAction.DEFAULT) {
      if (lastDefinitionStartPosition != null) {
        addDefinitionProduction(lastDefinitionStartPosition!!, lastDefinitionNextPosition!!)
      }
    }
    return super.acceptAction(action)
  }

  private fun addDefinitionProduction(startPosition: LookaheadText.Position, currentPosition: LookaheadText.Position) {
    val start = startPosition.offset + (startPosition.charsToNonWhitespace() ?: 0)
    val end = currentPosition.nextLineOrEofOffset
    val markerEnd = start + definitionMarker.length
    val definitionNode = SequentialParser.Node(start..end, DefinitionListMarkerProvider.DEFINITION)
    val markerNode = SequentialParser.Node(start..markerEnd, DefinitionListMarkerProvider.DEFINITION_MARKER)
    val contentNode = SequentialParser.Node(markerEnd..end, MarkdownElementTypes.PARAGRAPH)
    productionHolder.addProduction(listOf(contentNode, markerNode, definitionNode))
  }

  override fun getDefaultAction(): MarkerBlock.ClosingAction {
    return MarkerBlock.ClosingAction.DONE
  }

  override fun getDefaultNodeType(): IElementType {
    return DefinitionListMarkerProvider.DEFINITION_LIST
  }

  override fun isInterestingOffset(pos: LookaheadText.Position): Boolean {
    return pos.offsetInCurrentLine == -1
    //return true
  }

  companion object {
    private const val definitionMarker = ": "

    private fun ProductionHolder.addWrappedProduction(range: IntRange, type: IElementType) {
      val contentNode = SequentialParser.Node(range, MarkdownElementTypes.PARAGRAPH)
      val definitionNode = SequentialParser.Node(range, type)
      addProduction(listOf(contentNode, definitionNode))
    }
  }
}
