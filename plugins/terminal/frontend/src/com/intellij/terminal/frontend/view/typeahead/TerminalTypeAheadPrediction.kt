package com.intellij.terminal.frontend.view.typeahead

import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel

internal sealed interface TerminalTypeAheadPrediction {
  val position: TerminalLogicalPosition
  val isTentative: Boolean

  fun applyToModel(model: MutableTerminalOutputModel)

  fun getNewCursorPosition(): TerminalLogicalPosition

  fun isConfirmed(event: TerminalContentUpdatedEvent): PredictionConfirmationResult
}

internal enum class PredictionConfirmationResult {
  /** The prediction fully matches the content update event */
  CONFIRMED,

  /** The prediction text does not match the content update event text */
  TEXT_MISMATCH,

  /** The prediction is not yet applied in the content update event */
  NOT_APPLIED_YET
}