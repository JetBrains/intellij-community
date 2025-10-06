package com.intellij.terminal.frontend.view.impl

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.idea.AppModeAssertions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import org.jetbrains.plugins.terminal.block.reworked.MutableTerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalBlocksModel
import org.jetbrains.plugins.terminal.block.reworked.isCommandTypingMode
import org.jetbrains.plugins.terminal.block.reworked.updateContent
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor
import org.jetbrains.plugins.terminal.session.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.TerminalCursorPositionChangedEvent
import org.jetbrains.plugins.terminal.session.TerminalOutputEvent
import java.lang.Runnable

/**
 * Implementation of the [TerminalOutputModelController] that supports type-ahead.
 *
 * The approach is to delay applying the output updates from the backend for some time
 * if type-ahead prediction was applied to the output model.
 * This way, there will be no flickering when partial backend updates are applied on top of the predictions.
 * And if type-ahead predictions were incorrect, the user will see the actual state with a small delay.
 */
@OptIn(FlowPreview::class)
internal class TerminalTypeAheadOutputModelController(
  private val project: Project,
  private val outputModel: MutableTerminalOutputModel,
  private val blocksModel: TerminalBlocksModel,
  coroutineScope: CoroutineScope,
) : TerminalOutputModelController, TerminalTypeAhead {
  override val model: MutableTerminalOutputModel = outputModel

  private val delayedUpdateRequests: MutableSharedFlow<Unit> = MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  // Accessed only on EDT
  private var backendEventsDelayed: Boolean = false
  private var delayedEvents: MutableList<TerminalOutputEvent> = mutableListOf()

  init {
    coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      delayedUpdateRequests
        .debounce(BACKEND_EVENTS_DELAY_MILLIS)
        .collect {
          applyPendingUpdates()
        }
    }
  }

  private fun isTypeAheadEnabled(): Boolean {
    return Registry.`is`("terminal.type.ahead", false) && blocksModel.isCommandTypingMode()
  }

  override fun type(string: String) {
    if (!isTypeAheadEnabled()) return

    // At this moment we only support type-ahead at the end of the output
    if (outputModel.getTextAfterCursor().isBlank()) {
      updateOutputModel { outputModel.insertAtCursor(string) }
      delayUpdatesFromBackend()
      LOG.trace { "String typed prediction inserted: '$string'" }
    }
  }

  override fun backspace() {
    if (!isTypeAheadEnabled()) return

    val lastBlock = blocksModel.blocks.lastOrNull()
    val cursorOffset = outputModel.cursorOffsetState.value.toRelative()
    if (lastBlock == null || cursorOffset <= lastBlock.commandStartOffset || cursorOffset == 0) {
      // Cursor is placed before or at the command start, so we can't backspace anymore.
      return
    }

    // At this moment we only support type-ahead at the end of the output
    if (outputModel.getTextAfterCursor().isBlank()) {
      updateOutputModel { outputModel.backspace() }
      delayUpdatesFromBackend()
      LOG.trace { "Backspace prediction applied" }
    }
  }

  override fun updateContent(event: TerminalContentUpdatedEvent) {
    handleBackendEvent(event)
  }

  override fun updateCursorPosition(event: TerminalCursorPositionChangedEvent) {
    handleBackendEvent(event)
  }

  override fun applyPendingUpdates() {
    if (!backendEventsDelayed) return
    try {
      val merged = mergeOutputEvents(delayedEvents)
      doApplyDelayedEvents(merged)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      LOG.error("Error applying delayed events", e)
    }
    finally {
      delayedEvents.clear()
      backendEventsDelayed = false
    }
  }

  private fun handleBackendEvent(event: TerminalOutputEvent) {
    if (backendEventsDelayed) {
      // Type-ahead prediction was applied recently, let's delay the update from the backend
      delayedEvents.add(event)
    }
    else updateOutputModel { applyOutputEvent(event) }
  }

  private fun delayUpdatesFromBackend() {
    backendEventsDelayed = true
    check(delayedUpdateRequests.tryEmit(Unit))
  }

  /**
   * There might be events that override each other, so there is no need to apply them in the given order.
   * Let's filter out meaningless events.
   */
  private fun mergeOutputEvents(events: List<TerminalOutputEvent>): List<TerminalOutputEvent> {
    // Take only the last cursor position update if there are multiple updates
    val lastCursorUpdate = events.lastOrNull { it is TerminalCursorPositionChangedEvent }

    val contentEvents = events.filterIsInstance<TerminalContentUpdatedEvent>()
    if (contentEvents.isEmpty()) {
      return listOfNotNull(lastCursorUpdate)
    }

    // Merge consecutive updates of the content if it is on the same line.
    // Take only the last update on each line.
    val result = mutableListOf<TerminalOutputEvent>()
    var curIndex = 0
    while (curIndex < contentEvents.size) {
      val curLine = contentEvents[curIndex].startLineLogicalIndex
      while (curIndex < contentEvents.size && contentEvents[curIndex].startLineLogicalIndex == curLine) {
        curIndex++
      }
      result.add(contentEvents[curIndex - 1])
    }

    return if (lastCursorUpdate != null) {
      result + lastCursorUpdate
    }
    else result
  }

  private fun doApplyDelayedEvents(events: List<TerminalOutputEvent>) {
    updateOutputModel {
      for (event in events) {
        applyOutputEvent(event)
      }
    }
  }

  private fun applyOutputEvent(event: TerminalOutputEvent) {
    when (event) {
      is TerminalContentUpdatedEvent -> {
        outputModel.updateContent(event)
      }
      is TerminalCursorPositionChangedEvent -> {
        outputModel.updateCursorPosition(event.logicalLineIndex, event.columnIndex)
      }
      else -> error("Unexpected event type: ${event::class.simpleName}")
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

  companion object {
    private val LOG = logger<TerminalTypeAheadOutputModelController>()

    /**
     * The number of milliseconds to delay the output updates came from backend
     * after type-ahead prediction was applied.
     * The value differs for RemDev and monolith scenario.
     * In monolith, it is small enough to make the delay less noticeable when updates from the backend do not match the predictions.
     * In RemDev, it should be greater than the regular ping.
     */
    private val BACKEND_EVENTS_DELAY_MILLIS = if (AppModeAssertions.isMonolith()) 100L else 500L
  }
}

private fun MutableTerminalOutputModel.insertAtCursor(string: String) {
  withTypeAhead {
    val remainingLinePart = getRemainingLinePart()
    val replaceLength = string.length.coerceAtMost(remainingLinePart.length)
    val replaceOffset = cursorOffsetState.value
    replaceContent(replaceOffset, replaceLength, string, emptyList())
    // Do not reuse the cursorOffsetState.value because replaceContent might change it.
    // Instead, compute the new offset using the absolute offsets.
    val newCursorOffset = absoluteOffset(replaceOffset.toAbsolute() + string.length).coerceAtMost(relativeOffset(document.textLength))
    updateCursorPosition(newCursorOffset)
  }
}

private fun MutableTerminalOutputModel.backspace() {
  val offset = cursorOffsetState.value.toRelative()
  if (offset < 1) return
  val replaceOffset = relativeOffset(offset - 1)
  replaceContent(replaceOffset, 1, " ", emptyList())
  updateCursorPosition(replaceOffset)
}

private fun MutableTerminalOutputModel.getRemainingLinePart(): @NlsSafe String {
  val cursorOffset = cursorOffsetState.value.toRelative()
  val document = document
  val line = document.getLineNumber(cursorOffset)
  val lineEnd = document.getLineEndOffset(line)
  val remainingLinePart = document.getText(TextRange(cursorOffset, lineEnd))
  return remainingLinePart
}

private fun MutableTerminalOutputModel.getTextAfterCursor(): @NlsSafe String {
  val cursorOffset = cursorOffsetState.value.toRelative()
  return document.getText(TextRange(cursorOffset, document.textLength))
}