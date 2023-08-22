package org.intellij.plugins.markdown.lang.parser.blocks

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementType
import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.markerblocks.MarkerBlock
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider

class DefinitionListMarkerProvider: MarkerBlockProvider<MarkerProcessor.StateInfo> {
  override fun createMarkerBlocks(
    position: LookaheadText.Position,
    productionHolder: ProductionHolder,
    stateInfo: MarkerProcessor.StateInfo
  ): List<MarkerBlock> {
    val nextLinePosition = position.nextLinePosition() ?: return emptyList()
    if (!isDefinitionLine(nextLinePosition.currentLine)) {
      val afterNextPosition = nextLinePosition.nextLinePosition() ?: return emptyList()
      if (!isDefinitionLine(afterNextPosition.currentLine)) {
        return emptyList()
      }
    }
    return listOf(DefinitionListBlock(position, stateInfo.currentConstraints, productionHolder))
  }

  override fun interruptsParagraph(pos: LookaheadText.Position, constraints: MarkdownConstraints): Boolean {
    return false
  }

  companion object {
    @JvmField
    val DEFINITION_LIST: IElementType = MarkdownElementType("DEFINITION_LIST")

    @JvmField
    val TERM: IElementType = MarkdownElementType("TERM")

    @JvmField
    val DEFINITION: IElementType = MarkdownElementType("DEFINITION")

    @JvmField
    val DEFINITION_MARKER: IElementType = MarkdownElementType("DEFINITION_MARKER", isToken = true)

    fun isDefinitionLine(line: String): Boolean {
      return line.trimStart(' ', '\t').startsWith(": ")
    }
  }
}
