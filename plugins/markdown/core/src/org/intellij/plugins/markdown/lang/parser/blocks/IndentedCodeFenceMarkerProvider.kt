// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.parser.blocks

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.constraints.eatItselfFromString
import org.intellij.markdown.parser.constraints.extendsPrev
import org.intellij.markdown.parser.constraints.getCharsEaten
import org.intellij.markdown.parser.markerblocks.MarkerBlock
import org.intellij.markdown.parser.markerblocks.MarkerBlockImpl
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider
import org.intellij.markdown.parser.markerblocks.MarkdownParserUtil
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils

internal class IndentedCodeFenceMarkerProvider : MarkerBlockProvider<MarkerProcessor.StateInfo> {
  override fun createMarkerBlocks(
    pos: LookaheadText.Position,
    productionHolder: ProductionHolder,
    stateInfo: MarkerProcessor.StateInfo,
  ): List<MarkerBlock> {
    if (stateInfo.nextConstraints.getCharsEaten(pos.currentLine) > pos.offsetInCurrentLine) {
      return emptyList()
    }
    val opening = obtainOpeningInfo(pos, stateInfo.currentConstraints) ?: return emptyList()
    return listOf(IndentedCodeFenceMarkerBlock(
      stateInfo.currentConstraints,
      productionHolder,
      pos,
      opening,
      productionHolder.mark(),
      pos.offset,
    ))
  }

  override fun interruptsParagraph(pos: LookaheadText.Position, constraints: MarkdownConstraints): Boolean = false

  private fun obtainOpeningInfo(pos: LookaheadText.Position, constraints: MarkdownConstraints): OpeningInfo? {
    if (!MarkerBlockProvider.isStartOfLineWithConstraints(pos, constraints)) {
      return null
    }
    val charsToNonWhitespace = pos.charsToNonWhitespace() ?: return null
    val blockStart = pos.nextPosition(charsToNonWhitespace) ?: return null
    if (!MarkdownParserUtil.hasCodeBlockIndent(blockStart, constraints)) {
      return null
    }
    val match = OPENING_REGEX.matchEntire(pos.currentLineFromPosition) ?: return null
    val indentation = match.groups[1]?.value ?: return null
    val delimiter = match.groups[2]?.value ?: return null
    val infoGroup = match.groups[3] ?: return null
    val info = infoGroup.value
    if (delimiter.startsWith(":::") && info.trim().lowercase() != "mermaid") {
      return null
    }
    return OpeningInfo(
      indentation = indentation,
      delimiter = delimiter,
      info = info,
      infoStartOffset = pos.offset + infoGroup.range.first,
      infoEndOffset = pos.offset + infoGroup.range.last + 1,
    )
  }

  companion object {
    private val OPENING_REGEX = Regex("^([ \\t]*)(~~~++|```++|:::++)([^`\\r\\n]*+)\\r?$")
  }
}

private data class OpeningInfo(
  val indentation: String,
  val delimiter: String,
  val info: String,
  val infoStartOffset: Int,
  val infoEndOffset: Int,
) {
  val indentationColumns: Int = MarkdownCodeFenceUtils.getIndentationInfo(indentation).columns
}

private class IndentedCodeFenceMarkerBlock(
  constraints: MarkdownConstraints,
  private val productionHolder: ProductionHolder,
  startPosition: LookaheadText.Position,
  private val opening: OpeningInfo,
  marker: ProductionHolder.Marker,
  private val markerStartOffset: Int,
) : MarkerBlockImpl(constraints, marker) {
  private val lines = mutableListOf(
    Line(startPosition.offset, startPosition.nextLineOrEofOffset, startPosition.offset)
  )
  private var closingLine: Line? = null
  private var realInterestingOffset = -1

  override fun allowsSubBlocks(): Boolean = false

  override fun isInterestingOffset(pos: LookaheadText.Position): Boolean = true

  override fun calcNextInterestingOffset(pos: LookaheadText.Position): Int = pos.nextLineOrEofOffset

  override fun getDefaultAction(): MarkerBlock.ClosingAction = MarkerBlock.ClosingAction.DONE

  override fun doProcessToken(
    pos: LookaheadText.Position,
    currentConstraints: MarkdownConstraints,
  ): MarkerBlock.ProcessingResult {
    if (pos.offset < realInterestingOffset) {
      return MarkerBlock.ProcessingResult.CANCEL
    }
    if (pos.offsetInCurrentLine != -1) {
      return MarkerBlock.ProcessingResult.CANCEL
    }

    val nextLineConstraints = constraints.applyToNextLine(pos)
    if (!nextLineConstraints.extendsPrev(constraints)) {
      return MarkerBlock.ProcessingResult.DEFAULT
    }

    val lineStartOffset = pos.offset + 1
    val lineEndOffset = pos.nextLineOrEofOffset
    val constraintsLength = nextLineConstraints.getCharsEaten(pos.currentLine)
    val contentStartOffset = lineStartOffset + constraintsLength
    val line = nextLineConstraints.eatItselfFromString(pos.currentLine)
    val closingMarker = findClosingMarker(line)
    if (closingMarker != null) {
      closingLine = Line(
        codeLineStartOffset = contentStartOffset,
        endOffset = lineEndOffset,
        contentStartOffset = contentStartOffset,
      )
      scheduleProcessingResult(lineEndOffset, MarkerBlock.ProcessingResult.DEFAULT)
    }
    else {
      if (!hasCodeBlockIndent(line)) {
        return MarkerBlock.ProcessingResult.DEFAULT
      }
      lines += Line(
        codeLineStartOffset = contentStartOffset,
        endOffset = lineEndOffset,
        contentStartOffset = contentStartOffset,
      )
      realInterestingOffset = lineEndOffset
    }
    return MarkerBlock.ProcessingResult.CANCEL
  }

  override fun acceptAction(action: MarkerBlock.ClosingAction): Boolean {
    if (action == MarkerBlock.ClosingAction.DEFAULT || action == MarkerBlock.ClosingAction.DONE) {
      if (closingLine == null) {
        addCodeBlockProductions()
      }
      else {
        addCodeFenceProductions(closingLine!!)
      }
    }
    return super.acceptAction(action)
  }

  override fun getDefaultNodeType() = when {
    closingLine == null -> MarkdownElementTypes.CODE_BLOCK
    else -> MarkdownElementTypes.CODE_FENCE
  }

  private fun addCodeBlockProductions() {
    for ((codeLineStartOffset, endOffset, _) in lines) {
      if (codeLineStartOffset < endOffset) {
        productionHolder.addProduction(listOf(SequentialParser.Node(codeLineStartOffset..endOffset, MarkdownTokenTypes.CODE_LINE)))
      }
    }
  }

  private fun addCodeFenceProductions(closingLine: Line) {
    productionHolder.addProduction(listOf(
      SequentialParser.Node(markerStartOffset..opening.infoStartOffset, MarkdownTokenTypes.CODE_FENCE_START)
    ))
    if (opening.info.isNotEmpty()) {
      productionHolder.addProduction(listOf(
        SequentialParser.Node(opening.infoStartOffset..opening.infoEndOffset, MarkdownTokenTypes.FENCE_LANG)
      ))
    }
    for ((_, endOffset, contentStartOffset) in lines.drop(1)) {
      if (contentStartOffset < endOffset) {
        productionHolder.addProduction(listOf(
          SequentialParser.Node(contentStartOffset..endOffset, MarkdownTokenTypes.CODE_FENCE_CONTENT)
        ))
      }
    }
    productionHolder.addProduction(listOf(
      SequentialParser.Node(closingLine.codeLineStartOffset..closingLine.endOffset, MarkdownTokenTypes.CODE_FENCE_END)
    ))
  }

  private fun findClosingMarker(line: CharSequence): MarkerRange? {
    val indentation = MarkdownCodeFenceUtils.getIndentationInfo(line, opening.indentationColumns)
    if (indentation.columns < opening.indentationColumns) {
      return null
    }
    var markerStart = indentation.length
    while (markerStart < line.length && line[markerStart].isWhitespace()) {
      markerStart++
    }
    if (markerStart >= line.length || line[markerStart] != opening.delimiter[0]) {
      return null
    }
    var markerEnd = markerStart
    while (markerEnd < line.length && line[markerEnd] == opening.delimiter[0]) {
      markerEnd++
    }
    if (markerEnd - markerStart < opening.delimiter.length || line.subSequence(markerEnd, line.length).any { !it.isWhitespace() }) {
      return null
    }
    return MarkerRange(markerStart, markerEnd)
  }

  private fun hasCodeBlockIndent(line: CharSequence): Boolean {
    val indentation = MarkdownCodeFenceUtils.getIndentationInfo(line)
    return line.subSequence(indentation.length, line.length).all { it == '\r' }
           || indentation.columns >= 4
  }

  private data class Line(
    val codeLineStartOffset: Int,
    val endOffset: Int,
    val contentStartOffset: Int,
  )

  private data class MarkerRange(val startOffset: Int, val endOffset: Int)
}
