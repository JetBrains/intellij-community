package com.intellij.terminal.frontend.view.impl

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.idea.AppMode
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.jediterm.terminal.TextStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.block.output.TextStyleAdapter
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor
import org.jetbrains.plugins.terminal.session.impl.StyleRange
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCursorPositionChangedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent
import org.jetbrains.plugins.terminal.util.getNow
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import org.jetbrains.plugins.terminal.view.impl.updateContent
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandBlock
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration

/**
 * Implementation of the [TerminalOutputModelController] that supports type-ahead.
 *
 * The approach is to delay applying the output updates from the backend for some time
 * if type-ahead prediction was applied to the output model.
 * This way, there will be no flickering when partial backend updates are applied on top of the predictions.
 * And if type-ahead predictions were incorrect, the user will see the actual state with a small delay.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
internal class TerminalTypeAheadOutputModelController(
  private val project: Project,
  private val outputModel: MutableTerminalOutputModel,
  private val shellIntegrationDeferred: Deferred<TerminalShellIntegration>,
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
    if (!Registry.`is`("terminal.type.ahead", false)) return false
    val shellIntegration = shellIntegrationDeferred.getNow() ?: return false
    val activeBlock = shellIntegration.blocksModel.activeBlock as? TerminalCommandBlock ?: return false
    // Ensure that the active block has "commandStartOffset" set to protect prompt from deleting in typeahead logic.
    val isActiveBlockValid = activeBlock.commandStartOffset != null && activeBlock.outputStartOffset == null
    return shellIntegration.outputStatus.value == TerminalOutputStatus.TypingCommand && isActiveBlockValid
  }

  override fun type(string: String) {
    if (!isTypeAheadEnabled()) return

    // At this moment we only support type-ahead at the end of the output
    if (outputModel.getTextAfterCursor().isBlank()) {
      val textStyle = outputModel.predictTextStyleForTypingAt(outputModel.cursorOffset)
      updateOutputModel { outputModel.insertAtCursor(string, textStyle) }
      delayUpdatesFromBackend()
      LOG.trace { "String typed prediction inserted: '$string'" }
    }
  }

  override fun backspace() {
    if (!isTypeAheadEnabled()) return

    val shellIntegration = shellIntegrationDeferred.getCompleted()  // isTypeAheadEnabled should guarantee that it is available
    val commandBlock = shellIntegration.blocksModel.activeBlock as? TerminalCommandBlock
    val cursorOffset = outputModel.cursorOffset
    val commandStartOffset = commandBlock?.commandStartOffset
    if (commandBlock == null || (commandStartOffset != null && cursorOffset <= commandStartOffset) || cursorOffset == outputModel.startOffset) {
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
    private val BACKEND_EVENTS_DELAY_MILLIS = if (AppMode.isMonolith()) 100L else 500L
  }
}

/**
 * Tries to predict the style for the text on [offset] to match the style of the text before it.
 * Returns null if the style can't be predicted.
 */
private fun TerminalOutputModel.predictTextStyleForTypingAt(offset: TerminalOffset): TextStyle? {
  val lineIndex = getLineByOffset(offset)
  val lineStartOffset = getStartOfLine(lineIndex)
  if (offset == lineStartOffset || offset == startOffset) {
    // We can't predict the style for typing at the beginning of the line / model text
    return null
  }

  val previousOffset = offset - 1
  val textBefore = getText(previousOffset, offset).toString()
  if (textBefore.any { !it.isLetterOrDigit() }) {
    // Let's do not predict the style on typing after non-letter/digit characters.
    // For example, shell can highlight parenthesis differently than the text after them.
    return null
  }

  val highlighting = getHighlightingAt(previousOffset)
  val textStyleAdapter = highlighting?.textAttributesProvider as? TextStyleAdapter ?: return null
  return textStyleAdapter.style
}

/**
 * @param style a text style to apply to the inserted [string]. Null value means to use the default style.
 */
private fun MutableTerminalOutputModel.insertAtCursor(string: String, style: TextStyle? = null) {
  withTypeAhead {
    val remainingLinePart = getRemainingLinePart()
    val replaceLength = string.length.coerceAtMost(remainingLinePart.length)
    val replaceOffset = cursorOffset
    val styleRange = style?.let { StyleRange(0, string.length.toLong(), it, ignoreContrastAdjustment = false) }
    replaceContent(replaceOffset, replaceLength, string, listOfNotNull(styleRange))
    // Do not reuse the cursorOffsetState.value because replaceContent might change it.
    // Instead, compute the new offset using the absolute offsets.
    val newCursorOffset = TerminalOffset.of(replaceOffset.toAbsolute() + string.length).coerceAtMost(endOffset)
    updateCursorPosition(newCursorOffset)
  }
}

private fun MutableTerminalOutputModel.backspace() {
  val offset = cursorOffset
  if (offset <= startOffset) return
  val replaceOffset = offset - 1
  replaceContent(replaceOffset, 1, " ", emptyList())
  updateCursorPosition(replaceOffset)
}

private fun TerminalOutputModel.getRemainingLinePart(): @NlsSafe CharSequence {
  val cursorOffset = cursorOffset
  val line = getLineByOffset(cursorOffset)
  val lineEnd = getEndOfLine(line)
  val remainingLinePart = getText(cursorOffset, lineEnd)
  return remainingLinePart
}

private fun TerminalOutputModel.getTextAfterCursor(): @NlsSafe CharSequence {
  val cursorOffset = cursorOffset
  return getText(cursorOffset, endOffset)
}
