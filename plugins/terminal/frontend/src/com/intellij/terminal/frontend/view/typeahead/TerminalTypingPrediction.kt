package com.intellij.terminal.frontend.view.typeahead

import com.intellij.openapi.util.NlsSafe
import com.jediterm.terminal.TextStyle
import org.jetbrains.plugins.terminal.block.output.TextStyleAdapter
import org.jetbrains.plugins.terminal.session.impl.StyleRange
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.view.TerminalLineIndex
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel

internal data class TerminalTypingPrediction(
  override val position: TerminalLogicalPosition,
  private val text: String,
  override val isTentative: Boolean = false,
) : TerminalTypeAheadPrediction {
  override fun applyToModel(model: MutableTerminalOutputModel) {
    val replaceOffset = model.getStartOfLine(TerminalLineIndex.of(position.lineIndex)) + position.columnIndex.toLong()
    val remainingLinePart = model.getRemainingLinePart(replaceOffset)
    val replaceLength = text.length.coerceAtMost(remainingLinePart.length)
    val style = model.predictTextStyleForTypingAt(replaceOffset)
    val styleRange = style?.let { StyleRange(0, text.length.toLong(), it, ignoreContrastAdjustment = false) }

    model.replaceContent(replaceOffset, replaceLength, text, listOfNotNull(styleRange))
    val newCursorOffset = TerminalOffset.of(replaceOffset.toAbsolute() + text.length).coerceAtMost(model.endOffset)
    model.updateCursorPosition(newCursorOffset)
  }

  override fun getNewCursorPosition(): TerminalLogicalPosition {
    return TerminalLogicalPosition(position.lineIndex, position.columnIndex + text.length)
  }

  override fun isConfirmed(event: TerminalContentUpdatedEvent): PredictionConfirmationResult {
    if (event.startLineLogicalIndex > position.lineIndex) {
      return PredictionConfirmationResult.TEXT_MISMATCH
    }

    val lineStart = event.findLineStartOffset(position.lineIndex - event.startLineLogicalIndex)
                    ?: return PredictionConfirmationResult.TEXT_MISMATCH

    val textStart = lineStart + position.columnIndex
    val textEnd = textStart + text.length

    if (event.cursorLogicalLineIndex == position.lineIndex && event.cursorColumnIndex < position.columnIndex + text.length) {
      return PredictionConfirmationResult.NOT_APPLIED_YET
    }

    if (textEnd > event.text.length || event.text.substring(textStart, textEnd) != text) {
      return PredictionConfirmationResult.TEXT_MISMATCH
    }

    return PredictionConfirmationResult.CONFIRMED
  }

  private fun TerminalOutputModel.predictTextStyleForTypingAt(offset: TerminalOffset): TextStyle? {
    val lineIndex = getLineByOffset(offset)
    val lineStartOffset = getStartOfLine(lineIndex)
    if (offset == lineStartOffset || offset == startOffset) {
      return null
    }

    val previousOffset = offset - 1
    val textBefore = getText(previousOffset, offset).toString()
    if (textBefore.any { !it.isLetterOrDigit() }) {
      return null
    }

    val highlighting = getHighlightingAt(previousOffset)
    val textStyleAdapter = highlighting?.textAttributesProvider as? TextStyleAdapter ?: return null
    return textStyleAdapter.style
  }

  private fun TerminalOutputModel.getRemainingLinePart(fromOffset: TerminalOffset): @NlsSafe CharSequence {
    val line = getLineByOffset(fromOffset)
    val lineEnd = getEndOfLine(line)
    return getText(fromOffset, lineEnd)
  }
}