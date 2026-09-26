package com.intellij.terminal.frontend.view.typeahead

import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.dto.StyleRangeDto
import org.jetbrains.plugins.terminal.session.impl.dto.TextStyleDto
import org.jetbrains.plugins.terminal.view.TerminalLineIndex
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel

internal data class TerminalBackspacePrediction(
  override val position: TerminalLogicalPosition,
  override val isTentative: Boolean = false,
) : TerminalTypeAheadPrediction {
  override fun applyToModel(model: MutableTerminalOutputModel) {
    val offset = model.getStartOfLine(TerminalLineIndex.of(position.lineIndex)) + position.columnIndex.toLong()
    val replaceOffset = offset - 1
    model.replaceContent(replaceOffset, 1, " ", emptyList())
    model.updateCursorPosition(replaceOffset)
  }

  override fun getNewCursorPosition(): TerminalLogicalPosition {
    return TerminalLogicalPosition(position.lineIndex, position.columnIndex - 1)
  }

  override fun isConfirmed(event: TerminalContentUpdatedEvent): PredictionConfirmationResult {
    if (event.startLineLogicalIndex > position.lineIndex) {
      return PredictionConfirmationResult.TEXT_MISMATCH
    }

    val lineStart = event.findLineStartOffset(position.lineIndex - event.startLineLogicalIndex)
                    ?: return PredictionConfirmationResult.TEXT_MISMATCH

    val deletePos = lineStart + position.columnIndex - 1
    if (deletePos < lineStart) {
      return PredictionConfirmationResult.TEXT_MISMATCH
    }

    val lineEnd = event.text.indexOf('\n', lineStart).let { if (it == -1) event.text.length else it }
    val charAtDeletePos = if (deletePos < lineEnd) event.text[deletePos] else null

    if (event.cursorLogicalLineIndex == position.lineIndex && event.cursorColumnIndex == position.columnIndex) {
      return PredictionConfirmationResult.NOT_APPLIED_YET
    }

    if (event.cursorLogicalLineIndex == position.lineIndex
        && event.cursorColumnIndex < position.columnIndex
        && (charAtDeletePos == null || charAtDeletePos.isWhitespace() || hasGhostTextAfterCursor(event, lineStart, lineEnd))) {
      return PredictionConfirmationResult.CONFIRMED
    }

    return PredictionConfirmationResult.TEXT_MISMATCH
  }

  /**
   * Detects ghost text (shell autocompletion suggestions) after the cursor.
   *
   * Ghost text is identified when:
   * 1. There is exactly one unique style covering text after the cursor
   * 2. That style differs from all styles of the nearest token before the cursor
   */
  private fun hasGhostTextAfterCursor(
    event: TerminalContentUpdatedEvent,
    lineStart: Int,
    lineEnd: Int,
  ): Boolean {
    val cursorPos = lineStart + event.cursorColumnIndex
    if (cursorPos >= lineEnd) return false

    val afterCursorStyles = collectStylesInRange(event.styles, cursorPos.toLong(), lineEnd.toLong())
    if (afterCursorStyles.size != 1) return false
    val ghostStyle = afterCursorStyles.single()

    // Find nearest token before cursor (skip whitespace, then collect non-whitespace)
    var tokenEnd = cursorPos
    while (tokenEnd > lineStart && event.text[tokenEnd - 1].isWhitespace()) tokenEnd--
    if (tokenEnd == lineStart) return false

    var tokenStart = tokenEnd
    while (tokenStart > lineStart && !event.text[tokenStart - 1].isWhitespace()) tokenStart--

    val tokenStyles = collectStylesInRange(event.styles, tokenStart.toLong(), tokenEnd.toLong())
    return ghostStyle !in tokenStyles
  }

  private fun collectStylesInRange(styles: List<StyleRangeDto>, rangeStart: Long, rangeEnd: Long): Set<TextStyleDto> {
    val result = mutableSetOf<TextStyleDto>()
    for (range in styles) {
      if (range.startOffset < rangeEnd && range.endOffset > rangeStart) {
        result.add(range.style)
      }
    }
    return result
  }
}