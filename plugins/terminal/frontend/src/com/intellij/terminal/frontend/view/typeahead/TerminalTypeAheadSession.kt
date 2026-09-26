package com.intellij.terminal.frontend.view.typeahead

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel

internal class TerminalTypeAheadSession(
  private val project: Project,
  private val outputModel: MutableTerminalOutputModel,
  initialCursorPosition: TerminalLogicalPosition = outputModel.getCursorPosition(),
) {
  private val predictions: ArrayDeque<TerminalTypeAheadPrediction> = ArrayDeque()

  /** Matches a real cursor position initially, but can differ when tentative mode is active. */
  var cursorPosition: TerminalLogicalPosition = initialCursorPosition
    private set

  val firstPredictionLine: Long
    get() = predictions.firstOrNull()?.position?.lineIndex ?: -1

  val isTentativeMode: Boolean
    get() = predictions.lastOrNull()?.isTentative ?: false

  val predictionsCount: Int
    get() = predictions.size

  fun applyPrediction(prediction: TerminalTypeAheadPrediction): TerminalLogicalPosition {
    check(prediction.position == cursorPosition) { "Prediction position must match the current cursor position" }

    // Do not apply the prediction to the model if it's tentative, only store it in the prediction queue
    if (!prediction.isTentative) {
      applyPredictionToModel(prediction)
    }
    predictions.addLast(prediction)

    cursorPosition = prediction.getNewCursorPosition()
    return cursorPosition
  }

  fun confirmPredictions(event: TerminalContentUpdatedEvent): TypeAheadConfirmationResult {
    var confirmedCount = 0
    var textMismatch = false
    for (prediction in predictions) {
      val result = prediction.isConfirmed(event)
      when (result) {
        PredictionConfirmationResult.CONFIRMED -> {
          confirmedCount++
        }
        PredictionConfirmationResult.NOT_APPLIED_YET -> {
          break
        }
        PredictionConfirmationResult.TEXT_MISMATCH -> {
          textMismatch = true
          break
        }
      }
    }

    val predictionsSize = predictions.size
    repeat(confirmedCount) { predictions.removeFirst() }

    return if (textMismatch) {
      TypeAheadConfirmationResult.MismatchHappened
    }
    else if (confirmedCount == predictionsSize) {
      TypeAheadConfirmationResult.AllConfirmed
    }
    else {
      TypeAheadConfirmationResult.PartiallyConfirmed(confirmedCount, predictionsSize)
    }
  }

  private fun applyPredictionToModel(prediction: TerminalTypeAheadPrediction) {
    outputModel.withTypeAhead {
      updateOutputModel {
        prediction.applyToModel(outputModel)
      }
    }
  }

  private fun updateOutputModel(update: Runnable) {
    val lookup = LookupManager.getInstance(project).activeLookup
    if (lookup != null && lookup.editor.isReworkedTerminalEditor) {
      lookup.performGuardedChange(update)
    }
    else {
      update.run()
    }
  }
}

internal sealed class TypeAheadConfirmationResult {
  data object AllConfirmed : TypeAheadConfirmationResult()

  data class PartiallyConfirmed(val confirmedCount: Int, val totalCount: Int) : TypeAheadConfirmationResult()

  data object MismatchHappened : TypeAheadConfirmationResult()
}
