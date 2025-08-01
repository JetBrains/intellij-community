@file:OptIn(FlowPreview::class)

package com.intellij.terminal.backend.hyperlinks

import com.intellij.execution.filters.CompositeFilter
import com.intellij.execution.filters.Filter
import com.intellij.execution.impl.InlayProvider
import com.intellij.execution.impl.applyToLineRange
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.FrozenDocument
import com.intellij.openapi.progress.ProgressManager.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.terminal.session.TerminalHyperlinkId
import com.intellij.terminal.session.TerminalHyperlinksChangedEvent
import com.intellij.terminal.session.TerminalHyperlinksHeartbeatEvent
import com.intellij.terminal.session.dto.*
import com.intellij.util.asDisposable
import com.intellij.util.containers.ComparatorUtil.min
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.block.hyperlinks.CompositeFilterWrapper
import org.jetbrains.plugins.terminal.block.reworked.*
import java.util.*
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

internal class BackendTerminalHyperlinkHighlighter(
  project: Project,
  coroutineScope: CoroutineScope,
  private val outputModel: TerminalOutputModel,
  private val isInAlternateBuffer: Boolean,
) {

  private val hyperlinkId = AtomicLong()
  private val filterWrapper = CompositeFilterWrapper(project, coroutineScope)

  // These two are only modified from the model coroutine,
  // but can be read from other coroutines concurrently.
  private val currentTaskRunner = MutableStateFlow<HighlightTaskRunner?>(null)
  private val pendingTask = MutableStateFlow<HighlightTask?>(null)

  // Only ever accessed from the model coroutine.
  private var lastUsedFilter: CompositeFilter? = null

  val heartbeatFlow: Flow<TerminalHyperlinksHeartbeatEvent>
    get() = flow {
      while (true) {
        if (mayHaveWorkToDo()) {
          emit(TerminalHyperlinksHeartbeatEvent(isInAlternateBuffer))
        }
        delay(20.milliseconds)
      }
    }

  // If we have nothing to do, then both of these will be null.
  // If either is not null, it's possible that there will be no results,
  // but we may have to do some cleanup nevertheless or start a new task.
  private fun mayHaveWorkToDo(): Boolean = currentTaskRunner.value != null || pendingTask.value != null

  init {
    filterWrapper.getFilter() // kickstart computation
    outputModel.addListener(coroutineScope.asDisposable(), object : TerminalOutputModelListener {
      override fun afterContentChanged(model: TerminalOutputModel, startOffset: Int, isTypeAhead: Boolean) {
        val frozenModel = model.freeze()
        val existingPendingTask = pendingTask.value
        val startLine = frozenModel.relativeLine(frozenModel.document.getLineNumber(startOffset))
        val dirtyRegionStart = if (existingPendingTask == null) {
          startLine
        }
        else {
          min(existingPendingTask.startLine, startLine).coerceAtLeast(frozenModel.relativeLine(0))
        }
        val newPendingTask = HighlightTask(frozenModel, dirtyRegionStart)
        LOG.debug { "The model updated from offset $startOffset (line $startLine), the new task is $newPendingTask" }
        pendingTask.value = newPendingTask
      }
    })
    coroutineScope.launch(CoroutineName("running filters")) {
      currentTaskRunner.collect { runner ->
        runner?.run()
      }
    }
  }

  fun collectResultsAndMaybeStartNewTask(): TerminalHyperlinksChangedEvent? {
    val result = currentTaskRunner.value?.getNextOutputEvent { isValid(it) }
    maybeStartNewTask()
    return result
  }

  private fun isValid(taskResult: TaskResult): Boolean {
    val currentFilter = filterWrapper.getFilter()
    val currentTaskRunner = checkNotNull(currentTaskRunner.value) { "The task runner must be present since we have results" }
    if (currentTaskRunner.filter !== currentFilter) return false
    if (taskResult.startOffset < outputModel.relativeOffset(0)) return false // trimmed
    val pendingTask = pendingTask.value
    return if (pendingTask == null) {
      true // No updates since the current task started, therefore all results are valid
    }
    else {
      taskResult.endOffset <= pendingTask.startOffset
    }
  }

  private val TaskResult.startOffset: TerminalOffset get() = outputModel.absoluteOffset(absoluteStartOffset)
  private val TaskResult.endOffset: TerminalOffset get() = outputModel.absoluteOffset(absoluteEndOffset)

  private fun maybeStartNewTask() {
    val currentTaskRunner = currentTaskRunner.value
    var pendingTask = pendingTask.value
    val currentFilter = filterWrapper.getFilter()
    if (currentTaskRunner?.isRunning() == true) {
      LOG.debug { "Can't start a new task because ${currentTaskRunner.task} is still running" }
      return
    }
    val unprocessedResults = currentTaskRunner?.resultsCount()
    if (unprocessedResults != null && unprocessedResults > 0) {
      LOG.debug { "Can't start a new task because ${currentTaskRunner.task} has $unprocessedResults unprocessed results" }
      return
    }
    if (pendingTask?.hasWorkToDo() != true || currentFilter == null) {
      if (currentTaskRunner != null) {
        LOG.debug {
          "Finished ${currentTaskRunner.task}, " +
          "but can't start a new one because pendingTask = $pendingTask, currentFilter = $currentFilter"
        }
        this.currentTaskRunner.value = null
      }
      return
    }
    if (lastUsedFilter !== currentFilter) {
      LOG.debug { "The new task will process everything because of a filter change: $lastUsedFilter -> $currentFilter" }
      pendingTask = pendingTask.copy(startLine = pendingTask.outputModel.relativeLine(0))
    }
    this.pendingTask.value = null
    val newTaskRunner = HighlightTaskRunner(
      hyperlinkId = hyperlinkId,
      isInAlternateBuffer = isInAlternateBuffer,
      task = pendingTask,
      filter = currentFilter,
      continueCondition = { makesSenseToContinue(it) },
    )
    this.currentTaskRunner.value = newTaskRunner
    lastUsedFilter = currentFilter
  }

  private fun makesSenseToContinue(runner: HighlightTaskRunner): Boolean {
    val oldFilter = runner.filter
    val newFilter = filterWrapper.getFilter()
    if (newFilter !== oldFilter) {
      LOG.debug { "Stopping the task because the filter has changed from $oldFilter to $newFilter" }
      return false
    }
    val pendingTask = pendingTask.value
    if (pendingTask == null) {
      return true
    }
    val ourLine = runner.currentLine
    val nextUpdateLine = pendingTask.startLine
    return if (ourLine < nextUpdateLine) {
      true
    }
    else {
      LOG.debug {
        "Stopping the task because we have reached the line $ourLine, " +
        "which is beyond the newest changed line of $nextUpdateLine"
      }
      false
    }
  }

  @TestOnly
  internal suspend fun awaitTaskCompletion() {
    merge(currentTaskRunner, pendingTask)
      .first { !mayHaveWorkToDo() }
  }

}

private data class HighlightTask(
  val outputModel: FrozenTerminalOutputModel,
  val startLine: TerminalLine,
) {
  val endLineInclusive: TerminalLine =
    outputModel.relativeLine(outputModel.document.lineCount - 1)
  
  val startOffset: TerminalOffset =
    outputModel.relativeOffset(outputModel.document.getLineStartOffset(startLine.toRelative()))
  
  fun hasWorkToDo(): Boolean = endLineInclusive >= startLine

  override fun toString(): String =
    "HighlightTask(" +
    "outputModel=${describe(outputModel)}," +
    "startLine=$startLine," +
    "endLineInclusive=$endLineInclusive)"
}

private fun describe(outputModel: FrozenTerminalOutputModel) = buildString {
  append("OutputModel(trimmedChars=")
  append(outputModel.relativeOffset(0).toAbsolute())
  append(",trimmedLines=")
  append(outputModel.relativeLine(0).toAbsolute())
  append(",lengthChars=")
  append(outputModel.document.textLength)
  append(",lengthLines=")
  append(outputModel.document.lineCount)
  append(",modificationStamp=")
  append(outputModel.document.modificationStamp)
  append(")")
}

private typealias TaskResult = TerminalFilterResultInfoDto

private class HighlightTaskRunner(
  hyperlinkId: AtomicLong,
  private val isInAlternateBuffer: Boolean,
  val task: HighlightTask,
  val filter: CompositeFilter,
  private val continueCondition: (HighlightTaskRunner) -> Boolean,
) {
  private val isRunning = AtomicBoolean(true)
  private var isFirstEvent = true

  private val processor = HyperlinkProcessor(hyperlinkId)

  val topResults = LinkedBlockingDeque<TaskResult>()
  val bottomResults = LinkedBlockingDeque<TaskResult>()

  private val outputModel: FrozenTerminalOutputModel get() = task.outputModel
  private val document: FrozenDocument get() = outputModel.document

  private val topStartLine: TerminalLine = task.startLine
  private val bottomStartLine: TerminalLine = (lastLine() + 1 - BATCH_SIZE)
    .coerceAtLeast(firstLine())
    .coerceAtLeast(topStartLine)
  private val topStopLineInclusive: TerminalLine = bottomStartLine - 1
  private val bottomStopLineInclusive: TerminalLine = lastLine()

  var currentLine: TerminalLine = topStartLine

  fun isRunning(): Boolean = isRunning.get()

  fun resultsCount(): Int = topResults.size + bottomResults.size

  private fun firstLine() = outputModel.relativeLine(0)
  private fun lastLine() = outputModel.relativeLine(document.lineCount - 1)
  
  private operator fun TerminalLine.plus(count: Int) = outputModel.absoluteLine(toAbsolute() + count)
  private operator fun TerminalLine.minus(count: Int) = outputModel.absoluteLine(toAbsolute() - count)

  suspend fun run() {
    try {
      LOG.debug {
        "Started the task $task, " +
        "will process lines $topStartLine-$topStopLineInclusive at the top " +
        "and $bottomStartLine-$bottomStopLineInclusive at the bottom"
      }
      computeBottomResults()
      computeTopResults()
    }
    catch (e: Exception) {
      if (e is CancellationException) throw e
      LOG.error("Exception in the task $task", e)
    }
    finally {
      LOG.debug { "Finished the task $task" }
      isRunning.set(false)
    }
  }

  private suspend fun computeBottomResults() {
    val results = processor.processBatch(outputModel, filter, bottomStartLine, bottomStopLineInclusive)
    LOG.debug { "Produced at the bottom: ${describe(results)} in lines $bottomStartLine-$bottomStopLineInclusive" }
    bottomResults += results
  }

  private suspend fun computeTopResults() {
    var firstBatchLine = topStartLine
    while (firstBatchLine <= topStopLineInclusive && continueCondition(this)) {
      val lastBatchLine = (firstBatchLine + BATCH_SIZE - 1).coerceAtMost(topStopLineInclusive)
      val results = processor.processBatch(outputModel, filter, firstBatchLine, lastBatchLine)
      topResults += results
      LOG.debug { "Produced at the top: ${describe(results)} in lines $firstBatchLine-$lastBatchLine" }
      firstBatchLine = lastBatchLine + 1
      currentLine = firstBatchLine
    }
  }

  fun getNextOutputEvent(predicate: (TaskResult) -> Boolean): TerminalHyperlinksChangedEvent? {
    return createEvent(collectResults(predicate))
  }

  private fun collectResults(predicate: (TaskResult) -> Boolean): List<TaskResult> {
    val results = ArrayList<TaskResult>(topResults.size + bottomResults.size)
    collectValidAndRemoveInvalidResults(topResults, results, predicate)
    collectValidAndRemoveInvalidResults(bottomResults, results, predicate)
    LOG.debug { "Got ${describe(results)} from the task $task" }
    return results
  }

  private fun collectValidAndRemoveInvalidResults(from: Deque<TaskResult>, to: MutableList<TaskResult>, predicate: (TaskResult) -> Boolean) {
    // It's important to do everything in one loop because results are added asynchronously into the same deque.
    // If we try to split this thing into "remove trimmed - collect valid - remove invalid" parts,
    // we'll get flaky bugs because after removing trimmed results there may be nothing left,
    // but when we start collecting "valid" results,
    // it might happen that there are more trimmed (invalid) results are added as we go.
    var count = 0
    var valid = 0
    while (true) {
      val nextResult = from.pollFirst() ?: break
      ++count
      if (predicate(nextResult)) {
        to += nextResult
        ++valid
      }
    }
    LOG.debug { "Processed $count results, removed ${count - valid} invalid ones" }
  }

  private fun createEvent(hyperlinks: List<TaskResult>): TerminalHyperlinksChangedEvent? {
    var send: Boolean
    var remove: Boolean
    val isRunning = isRunning()
    when {
      hyperlinks.isNotEmpty() && isFirstEvent -> { // First results.
        remove = true // Because it's the first event for this affected range.
        send = true // Because we have something to send.
      }
      hyperlinks.isNotEmpty() && !isFirstEvent -> { // Not first results.
        remove = false // Already removed.
        send = true // Because we have something to send.
      }
      // now the hyperlinks.isEmpty() cases:
      isFirstEvent && isRunning -> { // No results yet, but the task is still running.
        remove = false
        send = false // Maybe the results just aren't ready yet, let's not remove to avoid flickering.
      }
      isFirstEvent && !isRunning -> { // No results at all.
        remove = true // The task is complete, no new links, but we must remove the old ones.
        send = true // No point in waiting as the task is complete.
      }
      // hyperlinks.isEmpty() && !isFirstEvent
      else -> { // There were some results, but there are no new ones.
        remove = false // Nothing to remove, nothing to report.
        send = false
      }
    }
    LOG.debug {
      "createEvent: isNotEmpty=${hyperlinks.isNotEmpty()}, isFirst=$isFirstEvent, isRunning=$isRunning => " +
      "send=$send, remove=$remove"
    }
    if (!send) return null
    isFirstEvent = false
    val result = TerminalHyperlinksChangedEvent(
      isInAlternateBuffer = isInAlternateBuffer,
      documentModificationStamp = task.outputModel.document.modificationStamp,
      removeFromOffset = if (remove) task.startOffset.toAbsolute() else null,
      hyperlinks = hyperlinks,
    )
    return result
  }

  private fun describe(results: List<TaskResult>) = buildString {
    if (results.isEmpty()) {
      append("no results")
      return@buildString
    }
    append(results.size).append(" results with offsets ")
    val minOffset = outputModel.absoluteOffset(results.minOf { it.absoluteStartOffset })
    val maxOffset = outputModel.absoluteOffset(results.maxOf { it.absoluteEndOffset })
    append(minOffset).append("-").append(maxOffset)
    val minLine = outputModel.relativeLine(document.getLineNumber(minOffset.toRelative()))
    val maxLine = outputModel.relativeLine(document.getLineNumber(maxOffset.toRelative()))
    append(", lines ").append(minLine).append("-").append(maxLine)
    append(" and IDs ")
    val minId = results.minOf { it.id.value }
    val maxId = results.maxOf { it.id.value }
    append(minId).append("-").append(maxId)
  }
}

private class HyperlinkProcessor(
  private val hyperlinkId: AtomicLong,
) {

  suspend fun processBatch(
    outputModel: FrozenTerminalOutputModel,
    filter: CompositeFilter,
    startLine: TerminalLine,
    endLine: TerminalLine,
  ): List<TerminalFilterResultInfoDto> =
    readAction {
      mutableListOf<TerminalFilterResultInfoDto>().also { results ->
        filter.applyToLineRange(outputModel.document, startLine.toRelative(), endLine.toRelative()) { applyResult ->
          checkCanceled()
          val hyperlinks = applyResult.filterResult?.resultItems?.mapNotNull { createHyperlinkOrHighlighting(outputModel, it) } ?: emptyList()
          results.addAll(hyperlinks)
        }
      }
    }

  private fun createHyperlinkOrHighlighting(
    outputModel: FrozenTerminalOutputModel,
    resultItem: Filter.ResultItem,
  ): TerminalFilterResultInfoDto? {
    val hyperlinkInfo = resultItem.hyperlinkInfo
    val highlightAttributes = resultItem.highlightAttributes
    val inlayProvider = resultItem as? InlayProvider
    return when {
      inlayProvider != null -> TerminalInlayInfoDto(
        id = TerminalHyperlinkId(hyperlinkId.incrementAndGet()),
        absoluteStartOffset = outputModel.relativeOffset(resultItem.highlightStartOffset).toAbsolute(),
        absoluteEndOffset = outputModel.relativeOffset(resultItem.highlightEndOffset).toAbsolute(),
        inlayProvider = inlayProvider,
      )
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

}

/**
 * Indicates the number of lines processed in one batch.
 *
 * Chosen to be large enough to cover a reasonably sized screen
 * because the last batch is processed with the highest priority.
 * Therefore, if we compute these many lines at once, there will be no flickering
 * in the visible part (assuming the terminal is scrolled to the bottom).
 */
private const val BATCH_SIZE = 200
private val LOG = logger<BackendTerminalHyperlinkHighlighter>()
