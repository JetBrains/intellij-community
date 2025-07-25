@file:OptIn(FlowPreview::class)

package com.intellij.terminal.backend.hyperlinks

import com.intellij.execution.filters.CompositeFilter
import com.intellij.execution.filters.Filter
import com.intellij.execution.impl.applyToLineRange
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.FrozenDocument
import com.intellij.openapi.progress.ProgressManager.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.terminal.session.TerminalHyperlinkId
import com.intellij.terminal.session.TerminalHyperlinksChangedEvent
import com.intellij.terminal.session.dto.TerminalFilterResultInfoDto
import com.intellij.terminal.session.dto.TerminalHighlightingInfoDto
import com.intellij.terminal.session.dto.TerminalHyperlinkInfoDto
import com.intellij.terminal.session.dto.toDto
import com.intellij.util.asDisposable
import com.intellij.util.containers.ComparatorUtil.min
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.block.hyperlinks.CompositeFilterWrapper
import org.jetbrains.plugins.terminal.block.reworked.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

internal class BackendTerminalHyperlinkHighlighter(
  project: Project,
  coroutineScope: CoroutineScope,
  private val outputModel: TerminalOutputModelImpl,
  private val isInAlternateBuffer: Boolean,
) {

  private val hyperlinkId = AtomicLong()
  private val highlightTask = MutableStateFlow<HighlightTask?>(highlightAllTask())
  private val filterWrapper = CompositeFilterWrapper(project, coroutineScope)

  val resultFlow: Flow<List<TerminalHyperlinksChangedEvent>>
    get() = channelFlow {
      val channel = this
      // asynchronous because we don't want to block the main event processing coroutine
      launch(CoroutineName("BackendTerminalHyperlinkHighlighter task processing")) {
        combine(highlightTask, filterWrapper.getFilterFlow(), transform = { task, filter -> Pair(task, filter) })
          .debounce(20.milliseconds)
          .collectLatest { (task, filter) ->
            LOG.debug { "Starting task $task" }
            try {
              task?.run(filter, channel)
            }
            finally {
              LOG.debug { "Finished task $task" }
            }
          }
      }
    }

  init {
    outputModel.addListener(coroutineScope.asDisposable(), object : TerminalOutputModelListener {
      override fun afterContentChanged(model: TerminalOutputModel, startOffset: Int, isTypeAhead: Boolean) {
        highlightTask.update { existingTask ->
          val frozenModel = outputModel.freeze()
          val dirtyRegionStart = if (existingTask == null) {
            frozenModel.relativeOffset(startOffset)
          }
          else {
            min(existingTask.startOffset, frozenModel.relativeOffset(startOffset)).coerceAtLeast(frozenModel.relativeOffset(0))
          }
          highlightTask(frozenModel, dirtyRegionStart)
        }
      }
    })
  }

  /**
   * Clears the current processing task and cancels it.
   *
   * Should be called manually when all the events produced by the current task are consumed and applied.
   * This ensures that the next model change will only process the region affected by that new change.
   * Otherwise, if the current task is still considered running, it'll re-process everything starting
   * from the minimum of the two offsets: the existing task's and the new change's.
   */
  fun finishCurrentTask() {
    highlightTask.value = null
  }

  private fun highlightAllTask(): HighlightTask = outputModel.freeze().let { frozen ->
    highlightTask(frozen, frozen.relativeOffset(0))
  }

  private fun highlightTask(outputModel: FrozenTerminalOutputModel, startOffset: TerminalOffset) =
    HighlightTask(
      hyperlinkId,
      isInAlternateBuffer,
      outputModel,
      startOffset,
    )

  @TestOnly
  internal suspend fun awaitTaskCompletion() {
    highlightTask.first { it == null }
  }

  private data class HighlightTask(
    private val hyperlinkId: AtomicLong,
    private val isInAlternateBuffer: Boolean,
    private val outputModel: FrozenTerminalOutputModel,
    val startOffset: TerminalOffset,
  ) {
    suspend fun run(filter: CompositeFilter, channel: SendChannel<List<TerminalHyperlinksChangedEvent>>) {
      val document = outputModel.document
      val firstID = hyperlinkId.get() + 1
      var firstOffset: TerminalOffset? = null
      var lastOffset: TerminalOffset? = null
      var count = 0
      LOG.debug {
        "Highlighting task started: " +
        "AltBuf=$isInAlternateBuffer, " +
        "startOffset=$startOffset, " +
        "trimmed=${outputModel.relativeOffset(0).toAbsolute()}"
      }
      fun logEvent(event: TerminalHyperlinksChangedEvent) {
        if (!LOG.isDebugEnabled) return
        count += event.hyperlinks.size
        val first = event.hyperlinks.lastOrNull() // reverse order
        val last = event.hyperlinks.firstOrNull()
        if (lastOffset == null && last != null) lastOffset = last.absoluteEndOffset.addRelative()
        if (first != null) firstOffset = first.absoluteStartOffset.addRelative()
        if (event.isFirstEventInTheBatch) LOG.debug("Sent the first event in the batch:")
        if (event.isLastEventInTheBatch) LOG.debug("Sent the last event in the batch:")
        LOG.debug(
          "event with ${event.hyperlinks.size} hyperlinks " +
          "${first?.absoluteStartOffset?.addRelative()}-${last?.absoluteEndOffset?.addRelative()} (IDs ${last?.id}-${first?.id})"
        )
      }
      suspend fun send(results: List<TerminalFilterResultInfoDto>, firstBatch: Boolean, lastBatch: Boolean) {
        require(!(firstBatch && lastBatch))
        if (results.isNotEmpty() || firstBatch || lastBatch) {
          val event = createEvent(results, firstBatch)
          channel.send(listOf(event))
          logEvent(event)
        }
      }
      val lineCount = document.lineCount
      if (lineCount == 0) {
        send(emptyList(), firstBatch = true, lastBatch = false)
        send(emptyList(), firstBatch = false, lastBatch = true)
        return
      }
      val startLineInclusive = document.getLineNumber(startOffset.toRelative())
      val endLineInclusive = lineCount - 1
      // Process in the reverse direction to highlight the visible part first.
      var endBatchInclusive = endLineInclusive
      while (endBatchInclusive >= startLineInclusive) {
        val endBatchExclusive = endBatchInclusive + 1
        val startBatchInclusive = (endBatchExclusive - BATCH_SIZE).coerceAtLeast(startLineInclusive)
        // For consistency, process the lines within the bach also in the reverse direction, hence the start/end are reversed:
        val batch = processBatch(document, filter, startLine = endBatchInclusive, endLine = startBatchInclusive)
        send(batch, firstBatch = endBatchInclusive == endLineInclusive, lastBatch = false)
        endBatchInclusive = startBatchInclusive - 1
      }
      send(emptyList(), firstBatch = false, lastBatch = true)
      val lastID = hyperlinkId.get()
      LOG.debug {
        "Highlighting task finished normally: " +
        "AltBuf=$isInAlternateBuffer, " +
        "startOffset=$startOffset, " +
        "trimmed=${outputModel.relativeOffset(0).toAbsolute()}, " +
        "processed ${endLineInclusive + 1 - startLineInclusive} lines, " +
        "generated $count links ${firstOffset}-${lastOffset} (IDs $firstID-$lastID)"
      }
    }

    private suspend fun processBatch(document: FrozenDocument, filter: CompositeFilter, startLine: Int, endLine: Int): List<TerminalFilterResultInfoDto> =
      readAction {
        mutableListOf<TerminalFilterResultInfoDto>().also { results ->
          filter.applyToLineRange(document, startLine, endLine) { applyResult ->
            checkCanceled()
            val hyperlinks = applyResult.filterResult?.resultItems?.mapNotNull { createHyperlinkOrHighlighting(it) } ?: emptyList()
            results.addAll(hyperlinks)
          }
        }
      }

    private fun Long.addRelative(): TerminalOffset = outputModel.absoluteOffset(this)

    private fun createHyperlinkOrHighlighting(resultItem: Filter.ResultItem): TerminalFilterResultInfoDto? {
      val hyperlinkInfo = resultItem.hyperlinkInfo
      val highlightAttributes = resultItem.highlightAttributes
      return when {
        hyperlinkInfo != null -> TerminalHyperlinkInfoDto(
          id = TerminalHyperlinkId(hyperlinkId.incrementAndGet()),
          hyperlinkInfo = hyperlinkInfo,
          absoluteStartOffset = outputModel.relativeOffset(resultItem.highlightStartOffset).toAbsolute(),
          absoluteEndOffset = outputModel.relativeOffset(resultItem.highlightEndOffset).toAbsolute(),
          style = highlightAttributes?.toDto(),
          followedStyle = resultItem.followedHyperlinkAttributes?.toDto(),
          hoveredStyle = resultItem.hoveredHyperlinkAttributes?.toDto(),
          layer = resultItem.highlighterLayer,
        )
        highlightAttributes != null -> TerminalHighlightingInfoDto(
          id = TerminalHyperlinkId(hyperlinkId.incrementAndGet()),
          absoluteStartOffset = outputModel.relativeOffset(resultItem.highlightStartOffset).toAbsolute(),
          absoluteEndOffset = outputModel.relativeOffset(resultItem.highlightEndOffset).toAbsolute(),
          style = highlightAttributes.toDto(),
          layer = resultItem.highlighterLayer,
        )
        else -> null
      }
    }

    private fun createEvent(hyperlinks: List<TerminalFilterResultInfoDto>, first: Boolean): TerminalHyperlinksChangedEvent {
      return TerminalHyperlinksChangedEvent(
        isInAlternateBuffer,
        outputModel.document.modificationStamp,
        if (first) startOffset.toAbsolute() else null,
        hyperlinks = hyperlinks,
      )
    }
  }

}

/**
 * Indicates the number of lines processed in one batch.
 *
 * When the model changes, the previously computed hyperlinks are removed only when the first batch
 * of computed hyperlinks arrives.
 * Because hyperlinks are computed from the end of the document in the reversed direction,
 * if we choose a value large enough for the batch size, it means that the visible hyperlinks
 * (assuming the terminal is scrolled to the end) will be cleared and added in one EDT event and there will be no flickering.
 * For example, even if the user has the terminal maximized on a portrait-oriented 4K monitor,
 * there will be approximately 130 lines visible, so 200 should be enough for most reasonable scenarios.
 */
private const val BATCH_SIZE = 200
private val LOG = logger<BackendTerminalHyperlinkHighlighter>()
