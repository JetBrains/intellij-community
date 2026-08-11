// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.parser.blocks

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementType
import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.markerblocks.MarkerBlock
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider
import org.jetbrains.annotations.ApiStatus

/**
 * Recognizes MkDocs / Python-Markdown style admonitions:
 *
 * ```
 * !!! type "Optional title"
 *     Indented body line(s).
 * ```
 *
 * The block opens with `!!!` (also `???` for collapsible variants) followed by a
 * type identifier and consumes subsequent lines that are either blank or indented
 * by at least four columns. The block is treated as opaque by the formatter so
 * that the title line and the four-space indent of the body are preserved.
 */
@ApiStatus.Experimental
class AdmonitionMarkerProvider : MarkerBlockProvider<MarkerProcessor.StateInfo> {
  override fun createMarkerBlocks(
    pos: LookaheadText.Position,
    productionHolder: ProductionHolder,
    stateInfo: MarkerProcessor.StateInfo,
  ): List<MarkerBlock> {
    if (!MarkerBlockProvider.isStartOfLineWithConstraints(pos, stateInfo.currentConstraints)) {
      return emptyList()
    }
    if (!isAdmonitionTitleLine(pos.currentLineFromPosition)) {
      return emptyList()
    }
    return listOf(AdmonitionBlock(pos, stateInfo.currentConstraints, productionHolder))
  }

  override fun interruptsParagraph(pos: LookaheadText.Position, constraints: MarkdownConstraints): Boolean {
    if (!MarkerBlockProvider.isStartOfLineWithConstraints(pos, constraints)) return false
    return isAdmonitionTitleLine(pos.currentLineFromPosition)
  }

  companion object {
    @JvmField
    val ADMONITION: IElementType = MarkdownElementType("ADMONITION")

    @JvmField
    val ADMONITION_TITLE: IElementType = MarkdownElementType("ADMONITION_TITLE", isToken = true)

    @JvmField
    val ADMONITION_CONTENT: IElementType = MarkdownElementType("ADMONITION_CONTENT", isToken = true)

    private val titleRegex = Regex("""(!!!|\?\?\?\+?)\s+[A-Za-z][\w-]*(\s+.*)?""")

    internal fun isAdmonitionTitleLine(line: CharSequence): Boolean {
      return titleRegex.matches(line)
    }

    /**
     * A content line is either blank or starts with at least four spaces of indentation
     * (or a single leading tab). The caller must already have stripped any active
     * Markdown constraints (block-quote arrows, list marker indent, etc.).
     */
    internal fun isAdmonitionContentLine(line: CharSequence): Boolean {
      if (line.all { it == ' ' || it == '\t' }) return true
      if (line.startsWith('\t')) return true
      return line.take(4).all { it == ' ' }
    }
  }
}
