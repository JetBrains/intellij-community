package org.intellij.plugins.markdown.lang.parser.blocks.frontmatter

import org.intellij.markdown.MarkdownElementType
import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.markerblocks.MarkerBlock
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider

/**
 * Provides support for front matter header blocks with:
 * * TOML content (delimited with `+++`)
 * * YAML content (delimited with `---`).
 *
 * Alternatively, YAML headers can be closed with triple dots (`...`)
 * as per [YAML spec](https://yaml.org/spec/1.2.2/#structures).
 *
 * Front matter header should *always* precede the document content.
 *
 * Example of TOML header:
 * ```markdown
 * +++
 * type: post
 * title: Some post
 * +++
 *
 * Some paragraph.
 * ```
 *
 * Example of YAML header:
 * ```markdown
 * ---
 * type: post
 * title: Some post
 * author: John
 * ---
 *
 * Some paragraph.
 * ```
 */
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
    return when (isOpeningDelimiterLine(possibleDelimiter)) {
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

    internal fun isOpeningDelimiterLine(line: String): Boolean {
      return isYamlDashedDelimiterLine(line) || isTomlDelimiterLine(line)
    }

    private fun isYamlDashedDelimiterLine(line: String): Boolean {
      return line.length >= 3 && line.all { it == '-' }
    }

    internal fun canBePairedWithClosingDots(opening: String): Boolean {
      return isYamlDashedDelimiterLine(opening)
    }

    private fun isYamlDottedDelimiterLine(line: String): Boolean {
      return line.length >= 3 && line.all { it == '.' }
    }

    internal fun isYamlDelimiters(opening: String, closing: String): Boolean {
      return isYamlDashedDelimiterLine(opening) && (isYamlDashedDelimiterLine(closing) || isYamlDottedDelimiterLine(closing))
    }

    private fun isTomlDelimiterLine(line: String): Boolean {
      return line.length >= 3 && line.all { it == '+' }
    }

    internal fun isTomlDelimiters(opening: String, closing: String): Boolean {
      return isTomlDelimiterLine(opening) && isTomlDelimiterLine(closing)
    }
  }
}
