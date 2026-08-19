package com.intellij.terminal.frontend.view.inlineCompletion

import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent.Backspace
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent.DocumentChange
import com.intellij.codeInsight.inline.completion.TypingEvent.OneSymbol
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.terminal.frontend.view.TerminalKeyEvent
import com.intellij.terminal.frontend.view.impl.syncEditorCaretWithModel
import com.intellij.terminal.frontend.view.impl.toRelative
import com.intellij.terminal.frontend.view.typeahead.TerminalBackspacePrediction
import com.intellij.terminal.frontend.view.typeahead.TerminalLogicalPosition
import com.intellij.terminal.frontend.view.typeahead.TerminalTypeAheadSession
import com.intellij.terminal.frontend.view.typeahead.TerminalTypingPrediction
import com.intellij.terminal.frontend.view.typeahead.TypeAheadConfirmationResult
import com.intellij.terminal.frontend.view.typeahead.logicalPositionToOffset
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
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
 * Connects terminal input to inline completion.
 *
 * The controller uses [TerminalTypeAheadSession] only to confirm the order of input events.
 * All predictions are tentative, so it never changes the terminal output model itself.
 */
@ApiStatus.Internal
class TerminalInlineCompletionController(
  private val project: Project,
  private val editor: EditorEx,
  private val model: MutableTerminalOutputModel,
  private val shellIntegration: TerminalShellIntegration,
  private val coroutineScope: CoroutineScope,
) {

  private var inputSession: TerminalTypeAheadSession? = null
  private val pendingEvents = ArrayDeque<PendingInputEvent>()
  private var lastTypedCommandText: String? = null

  @ApiStatus.Internal
  @VisibleForTesting
  fun stateForTest(): StateForTest {
    return StateForTest(
      hasInputSession = inputSession != null,
      pendingEventsCount = pendingEvents.size,
      predictionsCount = inputSession?.predictionsCount ?: 0,
    )
  }

  @OptIn(AwaitCancellationAndInvoke::class)
  fun install() {
    InlineCompletion.install(editor, coroutineScope)
    lastTypedCommandText = getCurrentTypedCommandText()
    coroutineScope.awaitCancellationAndInvoke(Dispatchers.UI) {
      InlineCompletion.remove(editor)
    }
  }

  fun handleKeyEvent(event: TerminalKeyEvent) {
    if (!(shellIntegration.outputStatus.value == TerminalOutputStatus.TypingCommand)) return

    when (event.awtEvent.id) {
      KeyEvent.KEY_TYPED -> if (!Character.isISOControl(event.awtEvent.keyChar)) {
        handleTyping(event.awtEvent.keyChar, event.cursorOffset)
      }
      KeyEvent.KEY_PRESSED -> when (event.awtEvent.keyCode) {
        KeyEvent.VK_BACK_SPACE -> if (event.awtEvent.hasNoModifiers()) {
          handleBackspace(event.cursorOffset)
        }
        KeyEvent.VK_TAB,
        KeyEvent.VK_LEFT,
        KeyEvent.VK_RIGHT,
        KeyEvent.VK_UP,
        KeyEvent.VK_DOWN,
        KeyEvent.VK_HOME,
        KeyEvent.VK_END -> {
          LOG.trace { "Inline completion input invalidated: pending=${pendingEvents.size}" }
          cancelCompletionAndClearSession()
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

  private fun handleTyping(char: Char, cursorOffset: TerminalOffset) {
    val session = getOrCreateInputSession(cursorOffset)
    val prediction = TerminalTypingPrediction(session.cursorPosition, char.toString(), isTentative = true)
    addPendingEvent(PendingInputEvent.Typing(char, prediction.position))
    session.applyPrediction(prediction)
    LOG.trace { "Inline completion input deferred: typing '$char'" }
  }

  private fun handleBackspace(cursorOffset: TerminalOffset) {
    val cursorPosition = inputSession?.cursorPosition ?: cursorOffset.toLogicalPosition()
    if (cursorPosition.columnIndex == 0) {
      LOG.trace("Inline completion ignored backspace at line start")
      return
    }
    val session = getOrCreateInputSession(cursorOffset)
    val prediction = TerminalBackspacePrediction(session.cursorPosition, isTentative = true)
    addPendingEvent(PendingInputEvent.Backspace())
    session.applyPrediction(prediction)
    LOG.trace("Inline completion input deferred: backspace")
  }

  private fun getOrCreateInputSession(cursorOffset: TerminalOffset): TerminalTypeAheadSession {
    return inputSession ?: TerminalTypeAheadSession(project, model, cursorOffset.toLogicalPosition()).also { inputSession = it }
  }

  private fun TerminalOffset.toLogicalPosition(): TerminalLogicalPosition {
    val line = model.getLineByOffset(this)
    return TerminalLogicalPosition(line.toAbsolute(), (this - model.getStartOfLine(line)).toInt())
  }

  private fun handleOutputModelUpdate() {
    if (!(shellIntegration.outputStatus.value == TerminalOutputStatus.TypingCommand)) return

    val outputEvent = createCurrentLineContentEvent()
    val typedCommandText = getCurrentTypedCommandText()
    LOG.trace {
      "Inline completion output received: position=${outputEvent.cursorLogicalLineIndex}:${outputEvent.cursorColumnIndex}, " +
      "session=${inputSession != null}"
    }
    if (inputSession != null) {
      dispatchConfirmedEvents(confirmPredictions(outputEvent))
    }
    else if (typedCommandText != lastTypedCommandText) {
      LOG.trace { "Inline completion cancelled by an unexpected output model update" }
      cancelCompletionAndClearSession()
    }
    lastTypedCommandText = typedCommandText
  }

  private fun cancelCompletionAndClearSession() {
    pendingEvents.forEach { it.timeoutJob?.cancel() }
    inputSession = null
    pendingEvents.clear()

    // Avoid scheduling an EDT action that acquires WIL when there is no session to cancel.
    if (InlineCompletionSession.getOrNull(editor) == null) return

    launchInlineCompletionAction {
      InlineCompletion.getHandlerOrNull(editor)?.cancel(FinishType.KEY_PRESSED)
    }
  }

  private fun getCurrentTypedCommandText(): String? {
    val activeBlock = shellIntegration.blocksModel.activeBlock as? TerminalCommandBlock ?: return null
    return activeBlock.getTypedCommandText(model)
  }

  private fun confirmPredictions(event: TerminalContentUpdatedEvent): Confirmation {
    val session = inputSession ?: return Confirmation.None
    return when (val result = session.confirmPredictions(event)) {
      TypeAheadConfirmationResult.AllConfirmed -> {
        LOG.trace { "Inline completion session resolved by output: result=$result, pending=${pendingEvents.size}" }
        Confirmation.Confirmed(pendingEvents.size)
      }
      is TypeAheadConfirmationResult.PartiallyConfirmed -> {
        LOG.trace { "Inline completion session resolved by output: result=$result, pending=${pendingEvents.size}" }
        Confirmation.Confirmed(result.confirmedCount)
      }
      TypeAheadConfirmationResult.MismatchHappened -> {
        LOG.trace { "Inline completion session resolved by output: result=$result, pending=${pendingEvents.size}" }
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
      Confirmation.Mismatch -> cancelCompletionAndClearSession()
      is Confirmation.Confirmed -> {
        repeat(confirmation.count) {
          val event = pendingEvents.removeFirst()
          event.timeoutJob?.cancel()
          when (event) {
            is PendingInputEvent.Typing -> invokeTyping(event.char, event.position)
            is PendingInputEvent.Backspace -> invokeBackspace()
          }
        }
        if (pendingEvents.isEmpty()) inputSession = null
      }
    }
  }

  private fun invokeTyping(char: Char, position: TerminalLogicalPosition) {
    LOG.trace { "Inline completion dispatched typing: char='$char', position=$position" }
    val offset = (model.logicalPositionToOffset(position) - model.startOffset).toInt()
    launchInlineCompletionAction {
      syncEditorCaretWithModel(editor, model)
      InlineCompletion.getHandlerOrNull(editor)?.invokeEvent(
        DocumentChange(OneSymbol(char, offset), editor)
      )
    }
  }

  private fun invokeBackspace() {
    LOG.trace("Inline completion dispatched backspace")
    launchInlineCompletionAction {
      syncEditorCaretWithModel(editor, model)
      InlineCompletion.getHandlerOrNull(editor)?.invokeEvent(Backspace(editor))
    }
  }

  private fun launchInlineCompletionAction(action: () -> Unit) {
    // Inline Completion may require the platform read lock, so execute actions later on EDT.
    coroutineScope.launch(Dispatchers.EDT) {
      action()
    }
  }

  private fun addPendingEvent(event: PendingInputEvent) {
    pendingEvents.addLast(event)
    event.timeoutJob = coroutineScope.launch(Dispatchers.UI) {
      delay(1.seconds)
      if (pendingEvents.any { it === event }) {
        cancelCompletionAndClearSession()
        LOG.trace {
          val input = when (event) {
            is PendingInputEvent.Typing -> "typing '${event.char}'"
            is PendingInputEvent.Backspace -> "backspace"
          }
          "Inline completion input timed out: $input"
        }
      }
    }
  }

  private sealed interface PendingInputEvent {
    data class Typing(
      val char: Char,
      val position: TerminalLogicalPosition,
      override var timeoutJob: Job? = null,
    ) : PendingInputEvent

    data class Backspace(
      override var timeoutJob: Job? = null,
    ) : PendingInputEvent

    var timeoutJob: Job?
  }

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
    private val LOG = logger<TerminalInlineCompletionController>()
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
