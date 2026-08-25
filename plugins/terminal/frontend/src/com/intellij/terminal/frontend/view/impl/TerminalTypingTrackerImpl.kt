package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.terminal.frontend.view.TerminalKeyEvent
import com.intellij.terminal.frontend.view.TerminalKeyEventImpl
import com.intellij.terminal.frontend.view.typeahead.TerminalBackspacePrediction
import com.intellij.terminal.frontend.view.typeahead.TerminalLogicalPosition
import com.intellij.terminal.frontend.view.typeahead.TerminalTypeAheadSession
import com.intellij.terminal.frontend.view.typeahead.TerminalTypingPrediction
import com.intellij.terminal.frontend.view.typeahead.TypeAheadConfirmationResult
import com.intellij.terminal.frontend.view.typeahead.logicalPositionToOffset
import com.intellij.util.containers.DisposableWrapperList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.util.fireListenersAndLogAllExceptions
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandBlock
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration
import org.jetbrains.plugins.terminal.view.shellIntegration.getTypedCommandText
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import kotlin.time.Duration.Companion.seconds

/**
 * Implementation of [TerminalTypingTracker]. Create instances via [createTypingTracker].
 *
 * Uses [TerminalTypeAheadSession] to confirm the order of input events.
 * Only reacts to key events and output updates while [TerminalShellIntegration.outputStatus] is
 * [TerminalOutputStatus.TypingCommand].
 */
@ApiStatus.Internal
class TerminalTypingTrackerImpl(
  private val project: Project,
  private val model: MutableTerminalOutputModel,
  private val shellIntegration: TerminalShellIntegration,
  private val coroutineScope: CoroutineScope,
) : TerminalTypingTracker {

  private val listeners = DisposableWrapperList<TerminalTypingListener>()

  private var inputSession: TerminalTypeAheadSession? = null
  private val pendingEvents = ArrayDeque<PendingInputEvent>()
  private var lastTypedCommandText: String? = getCurrentTypedCommandText()

  override fun addTypingListener(parentDisposable: Disposable, listener: TerminalTypingListener) {
    listeners.add(listener, parentDisposable)
  }

  @ApiStatus.Internal
  @VisibleForTesting
  fun stateForTest(): StateForTest {
    return StateForTest(
      hasInputSession = inputSession != null,
      pendingEventsCount = pendingEvents.size,
      predictionsCount = inputSession?.predictionsCount ?: 0,
    )
  }

  fun handleKeyEvent(event: TerminalKeyEvent) {
    if (shellIntegration.outputStatus.value != TerminalOutputStatus.TypingCommand) return

    when (event.awtEvent.id) {
      KeyEvent.KEY_TYPED -> if (!Character.isISOControl(event.awtEvent.keyChar)) {
        handleTyping(event, event.awtEvent.keyChar, event.cursorOffset)
      }
      KeyEvent.KEY_PRESSED -> when (event.awtEvent.keyCode) {
        KeyEvent.VK_BACK_SPACE -> if (event.awtEvent.hasNoModifiers()) {
          handleBackspace(event, event.cursorOffset)
        }
        KeyEvent.VK_TAB,
        KeyEvent.VK_LEFT,
        KeyEvent.VK_RIGHT,
        KeyEvent.VK_UP,
        KeyEvent.VK_DOWN,
        KeyEvent.VK_HOME,
        KeyEvent.VK_END,
          -> {
          LOG.trace { "Typing input invalidated by a navigation key: pending=${pendingEvents.size}" }
          cancelSessionAndNotifyMismatch()
        }
      }
    }
  }

  fun handleContentChanged() {
    handleOutputModelUpdate()
  }

  fun handleCursorOffsetChanged() {
    handleOutputModelUpdate()
  }

  private fun handleTyping(keyEvent: TerminalKeyEvent, char: Char, cursorOffset: TerminalOffset) {
    val session = getOrCreateInputSession(cursorOffset)
    val prediction = TerminalTypingPrediction(session.cursorPosition, char.toString(), isTentative = true)
    addPendingEvent(PendingInputEvent(keyEvent, prediction.position))
    session.applyPrediction(prediction)
    LOG.trace { "Typing input deferred: typing '$char'" }
  }

  private fun handleBackspace(keyEvent: TerminalKeyEvent, cursorOffset: TerminalOffset) {
    val cursorPosition = inputSession?.cursorPosition ?: cursorOffset.toLogicalPosition()
    if (cursorPosition.columnIndex == 0) {
      LOG.trace("Typing input ignored backspace at line start")
      return
    }
    val session = getOrCreateInputSession(cursorOffset)
    val prediction = TerminalBackspacePrediction(session.cursorPosition, isTentative = true)
    addPendingEvent(PendingInputEvent(keyEvent, prediction.position))
    session.applyPrediction(prediction)
    LOG.trace("Typing input deferred: backspace")
  }

  private fun getOrCreateInputSession(cursorOffset: TerminalOffset): TerminalTypeAheadSession {
    return inputSession ?: TerminalTypeAheadSession(project, model, cursorOffset.toLogicalPosition()).also { inputSession = it }
  }

  private fun TerminalOffset.toLogicalPosition(): TerminalLogicalPosition {
    val line = model.getLineByOffset(this)
    return TerminalLogicalPosition(line.toAbsolute(), (this - model.getStartOfLine(line)).toInt())
  }

  private fun handleOutputModelUpdate() {
    if (shellIntegration.outputStatus.value != TerminalOutputStatus.TypingCommand) return

    val outputEvent = createCurrentLineContentEvent()
    val typedCommandText = getCurrentTypedCommandText()
    LOG.trace {
      "Output received: position=${outputEvent.cursorLogicalLineIndex}:${outputEvent.cursorColumnIndex}, " +
      "session=${inputSession != null}"
    }
    if (inputSession != null) {
      dispatchConfirmedEvents(confirmPredictions(outputEvent))
    }
    else if (typedCommandText != lastTypedCommandText) {
      LOG.trace { "Mismatch: unexpected output model update with no pending typing" }
      cancelSessionAndNotifyMismatch()
    }
    lastTypedCommandText = typedCommandText
  }

  private fun cancelSessionAndNotifyMismatch() {
    pendingEvents.forEach { it.timeoutJob?.cancel() }
    inputSession = null
    pendingEvents.clear()
    fireEvent(TerminalTypingEvent.Mismatch)
  }

  private fun getCurrentTypedCommandText(): String? {
    val activeBlock = shellIntegration.blocksModel.activeBlock as? TerminalCommandBlock ?: return null
    return activeBlock.getTypedCommandText(model)
  }

  private fun confirmPredictions(event: TerminalContentUpdatedEvent): Confirmation {
    val session = inputSession ?: return Confirmation.None
    return when (val result = session.confirmPredictions(event)) {
      TypeAheadConfirmationResult.AllConfirmed -> {
        LOG.trace { "Session resolved by output: result=$result, pending=${pendingEvents.size}" }
        Confirmation.Confirmed(pendingEvents.size)
      }
      is TypeAheadConfirmationResult.PartiallyConfirmed -> {
        LOG.trace { "Session resolved by output: result=$result, pending=${pendingEvents.size}" }
        Confirmation.Confirmed(result.confirmedCount)
      }
      TypeAheadConfirmationResult.MismatchHappened -> {
        LOG.trace { "Session resolved by output: result=$result, pending=${pendingEvents.size}" }
        Confirmation.Mismatch
      }
    }
  }

  private fun createCurrentLineContentEvent(): TerminalContentUpdatedEvent {
    val cursorOffset = model.cursorOffset
    val line = model.getLineByOffset(cursorOffset)
    val lineStart = model.getStartOfLine(line)
    val lineEnd = model.getEndOfLine(line)
    return TerminalContentUpdatedEvent(
      text = model.getText(lineStart, lineEnd).toString(),
      styles = emptyList(),
      startLineLogicalIndex = line.toAbsolute(),
      cursorLogicalLineIndex = line.toAbsolute(),
      cursorColumnIndex = (cursorOffset - lineStart).toInt(),
    )
  }

  private fun dispatchConfirmedEvents(confirmation: Confirmation) {
    when (confirmation) {
      Confirmation.None -> Unit
      Confirmation.Mismatch -> cancelSessionAndNotifyMismatch()
      is Confirmation.Confirmed -> {
        repeat(confirmation.count) {
          val event = pendingEvents.removeFirst()
          event.timeoutJob?.cancel()
          fireEvent(TerminalTypingEvent.Confirmed(event.resolvedKeyEvent()))
        }
        if (pendingEvents.isEmpty()) inputSession = null
      }
    }
  }

  /**
   * The stored [PendingInputEvent.keyEvent] as originally received, with its [TerminalKeyEvent.cursorOffset]
   * corrected to the position this keystroke was predicted to land at. That predicted position can differ from
   * the offset the event originally carried when other keystrokes were still pending confirmation at the time
   * this one was typed.
   */
  private fun PendingInputEvent.resolvedKeyEvent(): TerminalKeyEvent {
    return TerminalKeyEventImpl(keyEvent.awtEvent, model.logicalPositionToOffset(position))
  }

  private fun fireEvent(event: TerminalTypingEvent) {
    fireListenersAndLogAllExceptions(listeners, LOG, "Exception during handling $event") {
      it.onTypingEvent(event)
    }
  }

  private fun addPendingEvent(event: PendingInputEvent) {
    pendingEvents.addLast(event)
    event.timeoutJob = coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
      delay(CONFIRMATION_AWAITING_TIME)
      if (pendingEvents.any { it === event }) {
        LOG.trace { "Typing input timed out: ${event.describeForLog()}" }
        cancelSessionAndNotifyMismatch()
      }
    }
  }

  private fun PendingInputEvent.describeForLog(): String =
    if (keyEvent.awtEvent.id == KeyEvent.KEY_TYPED) "typing '${keyEvent.awtEvent.keyChar}'" else "backspace"

  private data class PendingInputEvent(
    val keyEvent: TerminalKeyEvent,
    val position: TerminalLogicalPosition,
    var timeoutJob: Job? = null,
  )

  private sealed interface Confirmation {
    data object None : Confirmation
    data object Mismatch : Confirmation
    data class Confirmed(val count: Int) : Confirmation
  }

  @ApiStatus.Internal
  @VisibleForTesting
  data class StateForTest(
    val hasInputSession: Boolean,
    val pendingEventsCount: Int,
    val predictionsCount: Int,
  )

  companion object {
    private val LOG = logger<TerminalTypingTrackerImpl>()

    /**
     * Max time we wait for confirmation of typing by the output model change.
     * If it exceeds, we report a mismatch.
     */
    private val CONFIRMATION_AWAITING_TIME = 1.seconds
  }
}

private fun KeyEvent.hasNoModifiers(): Boolean {
  val nonTypingModifiers = InputEvent.ALT_DOWN_MASK or
                            InputEvent.ALT_GRAPH_DOWN_MASK or
                            InputEvent.CTRL_DOWN_MASK or
                            InputEvent.META_DOWN_MASK or
                            InputEvent.SHIFT_DOWN_MASK
  return modifiersEx and nonTypingModifiers == 0
}
