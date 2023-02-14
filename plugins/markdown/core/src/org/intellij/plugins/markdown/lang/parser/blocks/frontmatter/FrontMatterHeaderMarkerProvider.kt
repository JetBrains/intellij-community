package org.intellij.plugins.markdown.lang.parser.blocks.frontmatter

import com.intellij.openapi.util.registry.Registry
import org.intellij.markdown.MarkdownElementType
import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.markerblocks.MarkerBlock
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider

class FrontMatterHeaderMarkerProvider: MarkerBlockProvider<MarkerProcessor.StateInfo> {
  override fun createMarkerBlocks(
    position: LookaheadText.Position,
    productionHolder: ProductionHolder,
    stateInfo: MarkerProcessor.StateInfo
  ): List<MarkerBlock> {
    if (position.offset != 0) {
      return emptyList()
    }
    val possibleDelimiter = position.currentLine
    return when (isDelimiterLine(possibleDelimiter)) {
      true -> listOf(FrontMatterHeaderBlock(position, stateInfo.currentConstraints, productionHolder, possibleDelimiter))
      else -> emptyList()
    }
  }

  override fun interruptsParagraph(pos: LookaheadText.Position, constraints: MarkdownConstraints): Boolean {
    return false
  }

  companion object {
    @JvmField
    val FRONT_MATTER_HEADER = MarkdownElementType("FRONT_MATTER_HEADER")

    @JvmField
    val FRONT_MATTER_HEADER_DELIMITER = MarkdownElementType("FRONT_MATTER_HEADER_DELIMITER", isToken = true)

    @JvmField
    val FRONT_MATTER_HEADER_CONTENT = MarkdownElementType("FRONT_MATTER_HEADER_CONTENT", isToken = true)

    fun isDelimiterLine(line: String): Boolean {
      return isYamlDelimiterLine(line) || isTomlDelimiterLine(line)
    }

    fun isYamlDelimiterLine(line: String): Boolean {
      return line.length >= 3 && line.all { it == '-' }
    }

    fun isTomlDelimiterLine(line: String): Boolean {
      return line.length >= 3 && line.all { it == '+' }
    }

    @JvmStatic
    fun isFrontMatterSupportEnabled(): Boolean {
      return Registry.`is`("markdown.experimental.frontmatter.support.enable", false)
    }
  }
}
