package com.intellij.terminal.frontend.view.typeahead

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.idea.AppMode
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.view.impl.TerminalOutputModelController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.output.TextStyleAdapter
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCursorPositionChangedEvent
import org.jetbrains.plugins.terminal.session.impl.dto.Osc8HyperlinkDto
import org.jetbrains.plugins.terminal.session.impl.dto.StyleRangeDto
import org.jetbrains.plugins.terminal.session.impl.dto.toDto
import org.jetbrains.plugins.terminal.util.getNow
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import org.jetbrains.plugins.terminal.view.impl.updateContent
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandBlock
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

/**
 * The new experimental implementation of the [TerminalOutputModelController] that supports type-ahead.
 *
 * The general idea is to track various prediction types ([TerminalTypeAheadPrediction]) in [TerminalTypeAheadSession]
 * and try to confirm them one by one when new output events arrive.
 *
 * If only part of the predictions is confirmed, we do not apply the output event to the model to avoid visual interruptions.
 * It is especially important in the case with "ghost-text" inline suggestions - to not show intermediate ones.
 * If process output doesn't match predictions, we have to roll them back by applying the latest output model event.
 *
 * To protect from displaying predictions at incorrect moments, we use the *tentative* predictions approach.
 * See [shouldUseTentativePredictions] for more details.
 */
@ApiStatus.Internal
@OptIn(ExperimentalCoroutinesApi::class)
class TerminalTypeAheadOutputModelControllerV2(
  private val project: Project,
  private val outputModel: MutableTerminalOutputModel,
  private val shellIntegrationDeferred: Deferred<TerminalShellIntegration>,
  private val coroutineScope: CoroutineScope,
  private val enableInMonolith: Boolean = false,
) : TerminalTypeAheadOutputModelController {
  override val model: MutableTerminalOutputModel = outputModel

  private var lastContentEvent: TerminalContentUpdatedEvent = snapshotContent(outputModel)

  /** Context of the current type-ahead session */
  private var typeAheadSession: TerminalTypeAheadSession? = null

  /** Job for rolling back all predictions */
  private var rollbackJob: Job? = null

  /** Tracks cursor line changes and provides a mechanism to check if a line change has occurred recently. */
  private val cursorLineTracker = CursorLineTracker(
    initialLine = outputModel.cursorOffset.toAbsolute(),
    coroutineScope.childScope("CursorLineTracker")
  )

  private fun isTypeAheadEnabled(): Boolean {
    if (AppMode.isMonolith() && !enableInMonolith) return false
    if (!AdvancedSettings.getBoolean("terminal.type.ahead")) return false

    val shellIntegration = shellIntegrationDeferred.getNow() ?: return false
    val activeBlock = shellIntegration.blocksModel.activeBlock as? TerminalCommandBlock ?: return false
    val isActiveBlockValid = activeBlock.commandStartOffset != null && activeBlock.outputStartOffset == null
    return shellIntegration.outputStatus.value == TerminalOutputStatus.TypingCommand && isActiveBlockValid
  }

  /**
   * Determines whether a new prediction should be tentative.
   *
   * A prediction is tentative when:
   * 1. The last prediction in the queue is tentative (propagation).
   * 2. We are inside the post-line-change 500ms window.
   * 3. There is non-blank text after the cursor (ghost text or any text).
   *
   * Tentative predictions are not applied to the model and only tracked in the [typeAheadSession].
   * They are used in cases when we are not sure how the process responds to the event,
   * and they are necessary to indicate that there are some pending actions that are not yet confirmed by the process output.
   * So, when there are some unconfirmed tentative predictions, we can't apply any new prediction to the model.
   * Otherwise, it will be overriden by the pending output and cause blinking.
   */
  private fun shouldUseTentativePredictions(session: TerminalTypeAheadSession): Boolean {
    if (session.isTentativeMode) return true
    if (cursorLineTracker.isCursorLineChangedRecently) return true
    if (!outputModel.getTextAfterCursor().isBlank()) return true
    return false
  }

  private fun getOrCreateSession(): TerminalTypeAheadSession {
    return typeAheadSession ?: TerminalTypeAheadSession(project, outputModel).also {
      typeAheadSession = it
    }
  }

  override fun type(string: String) {
    LOG.trace { "Typing: '$string'" }

    if (!isTypeAheadEnabled()) return

    if (string == "\n") {
      handleEnter()
    }
    else handleTyping(string)
  }

  private fun handleEnter() {
    val session = getOrCreateSession()
    val isTentative = shouldUseTentativePredictions(session)
    val prediction = TerminalNewLinePrediction(session.cursorPosition, isTentative)
    val resultCursorPosition = session.applyPrediction(prediction)

    // The NewLinePrediction moves the cursor to a new line — trigger the line-change window.
    cursorLineTracker.cursorLineChanged(resultCursorPosition.lineIndex)

    scheduleRollbackJob()
    LOG.trace { "New line prediction inserted (tentative=$isTentative)" }
  }

  private fun handleTyping(string: String) {
    val session = getOrCreateSession()
    val isTentative = shouldUseTentativePredictions(session)
    val prediction = TerminalTypingPrediction(session.cursorPosition, string, isTentative)
    session.applyPrediction(prediction)

    scheduleRollbackJob()
    LOG.trace { "Typing prediction inserted (tentative=$isTentative): '$string'" }
  }

  override fun backspace() {
    LOG.trace { "Backspace" }

    if (!isTypeAheadEnabled()) return

    val shellIntegration = shellIntegrationDeferred.getCompleted()
    val commandBlock = shellIntegration.blocksModel.activeBlock as? TerminalCommandBlock
    val cursorPosition = typeAheadSession?.cursorPosition ?: outputModel.getCursorPosition()
    val cursorOffset = outputModel.logicalPositionToOffset(cursorPosition)
    val commandStartOffset = commandBlock?.commandStartOffset
    if (commandBlock == null
        || commandStartOffset != null && cursorOffset <= commandStartOffset
        || cursorPosition.columnIndex == 0
        || cursorOffset == outputModel.startOffset) {
      return
    }

    val session = getOrCreateSession()
    val isTentative = shouldUseTentativePredictions(session)
    val charToRemove = if (!isTentative) outputModel.getText(cursorOffset - 1, cursorOffset).toString()[0] else null
    val prediction = TerminalBackspacePrediction(cursorPosition, isTentative)
    session.applyPrediction(prediction)

    scheduleRollbackJob()
    LOG.trace { "Backspace prediction (tentative=$isTentative)" + if (charToRemove != null) ", removed '$charToRemove'" else "" }
  }

  override fun updateContent(event: TerminalContentUpdatedEvent) {
    cursorLineTracker.cursorLineChanged(event.cursorLogicalLineIndex)

    val session = typeAheadSession
    if (session == null) {
      lastContentEvent = event
      updateOutputModel { outputModel.updateContent(event) }
      LOG.trace { "No predictions, applying content update directly: $event" }
    }
    else {
      confirmOrReject(session, event, isCursorUpdateOnly = false)
      lastContentEvent = event
    }
  }

  override fun updateCursorPosition(event: TerminalCursorPositionChangedEvent) {
    cursorLineTracker.cursorLineChanged(event.logicalLineIndex)

    lastContentEvent = lastContentEvent.copy(
      cursorLogicalLineIndex = event.logicalLineIndex,
      cursorColumnIndex = event.columnIndex,
    )

    val session = typeAheadSession
    if (session == null) {
      updateOutputModel { outputModel.updateCursorPosition(event.logicalLineIndex, event.columnIndex) }
      LOG.trace { "No predictions, applying cursor position directly: $event" }
    }
    else {
      confirmOrReject(session, lastContentEvent, isCursorUpdateOnly = true)
    }
  }

  override fun applyPendingUpdates() {
    rollbackPredictions()
  }

  private fun confirmOrReject(
    session: TerminalTypeAheadSession,
    event: TerminalContentUpdatedEvent,
    isCursorUpdateOnly: Boolean,
  ) {
    LOG.trace {
      if (isCursorUpdateOnly) {
        "Trying to confirm cursor position update: $event"
      }
      else "Trying to confirm content update: $event"
    }

    val result: TypeAheadConfirmationResult = session.confirmPredictions(event)
    when (result) {
      TypeAheadConfirmationResult.AllConfirmed -> {
        LOG.trace { "All predictions confirmed. Applying final event." }
        updateOutputModel { outputModel.updateContent(event) }

        cancelTypeAheadSession()
      }
      is TypeAheadConfirmationResult.PartiallyConfirmed -> {
        if (session.isTentativeMode) {
          LOG.trace {
            val confirmationText = if (result.confirmedCount > 0) {
              "Partial confirmation (${result.confirmedCount} / ${result.totalCount})."
            }
            else "Predictions not yet confirmed."
            "$confirmationText Remaining predictions are tentative, applying event."
          }
          updateOutputModel { outputModel.updateContent(event) }
        }
        else {
          LOG.trace {
            val confirmationText = if (result.confirmedCount > 0) {
              "Partial confirmation (${result.confirmedCount} / ${result.totalCount})."
            }
            else "Predictions not yet confirmed."
            "$confirmationText Skipping event."
          }
        }

        scheduleRollbackJob()
      }
      TypeAheadConfirmationResult.MismatchHappened -> {
        if (event.startLineLogicalIndex > session.firstPredictionLine) {
          LOG.trace { "No predictions confirmed. Event is from a later line. Applying previous event to override false predictions + new event. Previous event: $lastContentEvent" }
          updateOutputModel {
            outputModel.updateContent(lastContentEvent)
            outputModel.updateContent(event)
          }
        }
        else {
          LOG.trace { "No predictions confirmed. Rejecting all and applying event." }
          updateOutputModel { outputModel.updateContent(event) }
        }

        cancelTypeAheadSession()
      }
    }
  }

  private fun rollbackPredictions() {
    val session = typeAheadSession ?: return
    cancelTypeAheadSession()

    updateOutputModel {
      outputModel.updateContent(lastContentEvent)
    }
    LOG.trace { "Rolled back ${session.predictionsCount} predictions, applied stored event: $lastContentEvent" }
  }

  private fun scheduleRollbackJob() {
    rollbackJob?.cancel()
    rollbackJob = coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      delay(PREDICTION_TIMEOUT_MS.milliseconds)
      rollbackPredictions()
    }
  }

  private fun cancelTypeAheadSession() {
    typeAheadSession = null
    rollbackJob?.cancel()
    rollbackJob = null
  }

  private fun updateOutputModel(update: Runnable) {
    val lookup = LookupManager.getInstance(project).activeLookup
    if (lookup != null && lookup.topLevelEditor.isReworkedTerminalEditor) {
      lookup.performGuardedChange(update)
    }
    else {
      update.run()
    }
  }

  private fun TerminalOutputModel.getTextAfterCursor(): @NlsSafe CharSequence {
    val cursorOffset = cursorOffset
    return getText(cursorOffset, endOffset)
  }

  private fun snapshotContent(model: TerminalOutputModel): TerminalContentUpdatedEvent {
    val text = model.getText(model.startOffset, model.endOffset).toString()
    val highlightings = model.getHighlightings()
    val styles = buildList {
      for (i in 0 until highlightings.size) {
        val h = highlightings[i]
        val adapter = h.textAttributesProvider as? TextStyleAdapter ?: continue
        add(StyleRangeDto(
          startOffset = h.startOffset.toLong(),
          endOffset = h.endOffset.toLong(),
          style = adapter.style.toDto(),
          ignoreContrastAdjustment = adapter.ignoreContrastAdjustment,
        ))
      }
    }
    // Offsets are relative to `startOffset` (== the start of `firstLineIndex`), same as the styles above.
    val osc8Hyperlinks = model.getOsc8Hyperlinks().map { link ->
      Osc8HyperlinkDto(
        startOffset = link.startOffset - model.startOffset,
        endOffset = link.endOffset - model.startOffset,
        uri = link.uri,
      )
    }
    val cursorOffset = model.cursorOffset
    val lineIndex = model.getLineByOffset(cursorOffset)
    val lineStart = model.getStartOfLine(lineIndex)
    return TerminalContentUpdatedEvent(
      text = text,
      styles = styles,
      startLineLogicalIndex = model.firstLineIndex.toAbsolute(),
      cursorLogicalLineIndex = lineIndex.toAbsolute(),
      cursorColumnIndex = (cursorOffset - lineStart).toInt(),
      osc8Hyperlinks = osc8Hyperlinks,
    )
  }

  private class CursorLineTracker(
    initialLine: Long,
    private val coroutineScope: CoroutineScope,
  ) {
    private var lastKnownCursorLine: Long = initialLine
    private val isCursorLineChangedRecentlyProp = AtomicBoolean(false)
    private var cursorLineChangedResetJob: Job? = null

    val isCursorLineChangedRecently: Boolean
      get() = isCursorLineChangedRecentlyProp.get()

    fun cursorLineChanged(newLine: Long) {
      if (newLine != lastKnownCursorLine) {
        lastKnownCursorLine = newLine
        isCursorLineChangedRecentlyProp.set(true)

        cursorLineChangedResetJob?.cancel()
        cursorLineChangedResetJob = coroutineScope.launch {
          delay(PREDICTION_TIMEOUT_MS.milliseconds)
          isCursorLineChangedRecentlyProp.set(false)
        }
      }
    }
  }

  companion object {
    private val LOG = logger<TerminalTypeAheadOutputModelControllerV2>()
    private const val PREDICTION_TIMEOUT_MS = 500
  }
}