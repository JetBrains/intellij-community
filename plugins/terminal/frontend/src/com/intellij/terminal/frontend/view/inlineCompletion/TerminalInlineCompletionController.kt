package com.intellij.terminal.frontend.view.inlineCompletion

import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.TypingEvent
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.terminal.frontend.view.impl.syncEditorCaretWithModel
import com.intellij.terminal.frontend.view.typeahead.TerminalBackspacePrediction
import com.intellij.terminal.frontend.view.typeahead.TerminalLogicalPosition
import com.intellij.terminal.frontend.view.typeahead.TerminalTypeAheadSession
import com.intellij.terminal.frontend.view.typeahead.TerminalTypingPrediction
import com.intellij.terminal.frontend.view.typeahead.TypeAheadConfirmationResult
import com.intellij.terminal.frontend.view.typeahead.logicalPositionToOffset
import com.intellij.util.asDisposable
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalCursorOffsetChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import kotlin.time.Duration.Companion.seconds

/**
 * Connects terminal input to inline completion.
 *
 * The controller uses [TerminalTypeAheadSession] only to confirm the order of input events.
 * All predictions are tentative, so it never changes the terminal output model itself.
 */
internal class TerminalInlineCompletionController(
  private val project: Project,
  private val editor: EditorEx,
  private val model: MutableTerminalOutputModel,
  private val coroutineScope: CoroutineScope,
) {

  private var inputSession: TerminalTypeAheadSession? = null
  private val pendingEvents = ArrayDeque<PendingInputEvent>()

  fun install() {
    InlineCompletion.install(editor, coroutineScope)
    model.addListener(coroutineScope.asDisposable(), object : TerminalOutputModelListener {

      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        handleOutputModelUpdate()
      }

      override fun cursorOffsetChanged(event: TerminalCursorOffsetChangeEvent) {
        handleOutputModelUpdate()
      }
    })
    coroutineScope.awaitCancellationAndInvoke(Dispatchers.EDT) {
      InlineCompletion.remove(editor)
    }
  }

  fun handleInput(event: TerminalInlineCompletionInputEvent, cursorOffset: TerminalOffset) {
    when (event) {
      is TerminalInlineCompletionInputEvent.Typing -> handleTyping(event, cursorOffset)
      TerminalInlineCompletionInputEvent.Backspace -> handleBackspace(cursorOffset)
      TerminalInlineCompletionInputEvent.Invalidate -> {
        LOG.trace { "Inline completion input invalidated: pending=${pendingEvents.size}" }
        cancelCompletionAndClearSession()
      }
    }
  }

  private fun handleTyping(event: TerminalInlineCompletionInputEvent.Typing, cursorOffset: TerminalOffset) {
    val session = getOrCreateInputSession()
    val prediction = TerminalTypingPrediction(session.cursorPosition, event.char.toString(), isTentative = true)
    addPendingEvent(PendingInputEvent.Typing(event, prediction.position))
    session.applyPrediction(prediction)
    LOG.trace { "Inline completion input deferred: typing '${event.char}'" }
  }

  private fun handleBackspace(cursorOffset: TerminalOffset) {
    val session = getOrCreateInputSession()
    if (session.cursorPosition.columnIndex == 0) {
      LOG.trace("Inline completion ignored backspace at line start")
      return
    }
    val prediction = TerminalBackspacePrediction(session.cursorPosition, isTentative = true)
    addPendingEvent(PendingInputEvent.Backspace())
    session.applyPrediction(prediction)
    LOG.trace("Inline completion input deferred: backspace")
  }

  private fun getOrCreateInputSession(): TerminalTypeAheadSession {
    return inputSession ?: TerminalTypeAheadSession(project, model).also { inputSession = it }
  }

  private fun handleOutputModelUpdate() {
    val outputEvent = createCurrentLineContentEvent()
    LOG.trace {
      "Inline completion output received: position=${outputEvent.cursorLogicalLineIndex}:${outputEvent.cursorColumnIndex}, " +
      "session=${inputSession != null}"
    }
    if (inputSession != null) {
      dispatchConfirmedEvents(confirmPredictions(outputEvent))
    }
    else {
      LOG.trace { "Inline completion cancelled by an unexpected output model update" }
      cancelCompletionAndClearSession()
    }
  }

  private fun cancelCompletionAndClearSession() {
    pendingEvents.forEach { it.timeoutJob?.cancel() }
    inputSession = null
    pendingEvents.clear()
    InlineCompletion.getHandlerOrNull(editor)?.cancel(FinishType.KEY_PRESSED)
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
            is PendingInputEvent.Typing -> invokeTyping(event.input, event.position)
            is PendingInputEvent.Backspace -> invokeBackspace()
          }
        }
        if (pendingEvents.isEmpty()) inputSession = null
      }
    }
  }

  private fun invokeTyping(input: TerminalInlineCompletionInputEvent.Typing, position: TerminalLogicalPosition) {
    LOG.trace { "Inline completion dispatched typing: char='${input.char}', position=$position" }
    syncEditorCaretWithModel(editor, model)
    InlineCompletion.getHandlerOrNull(editor)?.invokeEvent(
      InlineCompletionEvent.DocumentChange(TypingEvent.OneSymbol(input.char, (model.logicalPositionToOffset(position) - model.startOffset).toInt()), editor)
    )
  }

  private fun invokeBackspace() {
    LOG.trace("Inline completion dispatched backspace")
    syncEditorCaretWithModel(editor, model)
    InlineCompletion.getHandlerOrNull(editor)?.invokeEvent(InlineCompletionEvent.Backspace(editor))
  }

  private fun addPendingEvent(event: PendingInputEvent) {
    pendingEvents.addLast(event)
    event.timeoutJob = coroutineScope.launch(Dispatchers.EDT) {
      delay(1.seconds)
      if (pendingEvents.any { it === event }) {
        cancelCompletionAndClearSession()
        LOG.trace {
          val input = when (event) {
            is PendingInputEvent.Typing -> "typing '${event.input.char}'"
            is PendingInputEvent.Backspace -> "backspace"
          }
          "Inline completion input timed out: $input"
        }
      }
    }
  }

  private sealed interface PendingInputEvent {
    data class Typing(
      val input: TerminalInlineCompletionInputEvent.Typing,
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

  companion object {
    private val LOG = logger<TerminalInlineCompletionController>()
  }
}
