package com.intellij.terminal.frontend.view.typeahead

import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.view.TerminalLineIndex
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel

internal data class TerminalNewLinePrediction(
  override val position: TerminalLogicalPosition,
  override val isTentative: Boolean,
) : TerminalTypeAheadPrediction {
  override fun applyToModel(model: MutableTerminalOutputModel) {
    val currentLine = TerminalLineIndex.of(position.lineIndex)
    if (currentLine >= model.lastLineIndex) {
      model.replaceContent(model.endOffset, 0, "\n", emptyList())
    }
    val nextLineStart = model.getStartOfLine(TerminalLineIndex.of(position.lineIndex + 1))
    model.updateCursorPosition(nextLineStart)
  }

  override fun getNewCursorPosition(): TerminalLogicalPosition {
    return TerminalLogicalPosition(position.lineIndex + 1, 0)
  }

  override fun isConfirmed(event: TerminalContentUpdatedEvent): PredictionConfirmationResult {
    return when {
      event.cursorLogicalLineIndex > position.lineIndex -> PredictionConfirmationResult.CONFIRMED
      event.cursorLogicalLineIndex == position.lineIndex -> PredictionConfirmationResult.NOT_APPLIED_YET
      else -> PredictionConfirmationResult.TEXT_MISMATCH
    }
  }
}