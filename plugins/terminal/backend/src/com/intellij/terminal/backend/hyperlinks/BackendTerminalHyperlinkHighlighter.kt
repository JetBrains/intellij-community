@file:OptIn(FlowPreview::class)

package com.intellij.terminal.backend.hyperlinks

import com.intellij.execution.filters.CompositeFilter
import com.intellij.execution.filters.Filter
import com.intellij.execution.impl.HypertextInput
import com.intellij.execution.impl.InlayProvider
import com.intellij.execution.impl.applyToLineRange
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager.checkCanceled
import com.intellij.util.text.CharArrayUtil
import com.intellij.util.text.ImmutableCharSequence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkId
import org.jetbrains.plugins.terminal.hyperlinks.TerminalOutputContentUpdate
import org.jetbrains.plugins.terminal.hyperlinks.filter.CompositeFilterWrapper
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalFilterResultInfoDto
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHighlightingInfoDto
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinkInfoDto
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksOutputEvent
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalInlayInfoDto
import org.jetbrains.plugins.terminal.hyperlinks.session.toDto
import org.jetbrains.plugins.terminal.view.TerminalLineIndex
import org.jetbrains.plugins.terminal.view.TerminalOffset
import java.awt.event.MouseEvent
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JLabel

internal class BackendTerminalHyperlinkHighlighter(
  private val filterWrapper: CompositeFilterWrapper,
  coroutineScope: CoroutineScope,
) {

  private val hyperlinkId = AtomicLong()

  // The state is only modified from the model coroutine but can be read concurrently.
  private val currentTaskState = MutableStateFlow(TaskState(null, null))

  // Could've used update { ... } for flows, but let's use plain assignment to highlight that there are no concurrent updates.

  private var currentTaskRunner: HighlightTaskRunner?
    get() = currentTaskState.value.currentTaskRunner
    set(value) {
      currentTaskState.value = currentTaskState.value.copy(currentTaskRunner = value)
    }

  private var pendingTask: HighlightTask?
    get() = currentTaskState.value.pendingTask
    set(value) {
      currentTaskState.value = currentTaskState.value.copy(pendingTask = value)
    }

  /**
   * The latest known trim threshold.
   * Used by [buildEvent] to drop results whose offsets fall in a region trimmed after the running task started.
   * Updated on every content update by [applyUpdate].
   */
  private var currentTrimStartOffset: TerminalOffset = TerminalOffset.of(0)

  private val fakeMouseEventJob = coroutineScope.async(Dispatchers.UI + ModalityState.any().asContextElement()) {
    MouseEvent(JLabel(), MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 0, 0, 0, 0, 1, true, MouseEvent.BUTTON2)
  }

  /**
   * Returns a fake mouse event to be used in [com.intellij.execution.filters.HyperlinkWithPopupMenuInfo.getPopupMenuGroup].
   *
   * It's not actually used by any implementation at the moment of writing, but there are external usages with null checks,
   * so passing `null` will cause them to throw NPE. Which is why this hack exists, for API compatibility only.
   *
   * Guaranteed to successfully return a non-null event if accessed after the first hyperlink is computed,
   * which, in itself, is guaranteed by the fact that nobody can invoke a context menu for a hyperlink that doesn't exist yet.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  val fakeMouseEvent: MouseEvent
    get() = fakeMouseEventJob.getCompleted()

  fun mayHaveWorkToDo(): Boolean = currentTaskState.value.mayHaveWorkToDo()

  init {
    coroutineScope.launch(CoroutineName("running filters")) {
      fakeMouseEventJob.await() // must complete before any attempt to show a context menu for a HyperlinkWithPopupMenuInfo
      currentTaskState.mapNotNull { it.currentTaskRunner }.distinctUntilChanged().collect { runner ->
        runner.run()
      }
    }
  }

  fun applyUpdate(update: TerminalOutputContentUpdate) {
    // Every update carries the current trim threshold.
    // Apply it first (so [isValid] drops trimmed results and the pending task is clamped), then merge in the new content.
    currentTrimStartOffset = update.trimStartOffset
    val clamped = pendingTask?.let { clampHighlightTask(it, update.trimStartLine, update.trimStartOffset) }
    val newPendingTask = if (clamped != null) {
      mergeContentUpdate(clamped, update)
    }
    else {
      HighlightTask(
        charsSequence = CharArrayUtil.createImmutableCharSequence(update.charsSequence),
        startLine = update.startLine,
        endLine = update.endLine,
        startOffset = update.startOffset,
        modificationStamp = update.modificationStamp,
      )
    }
    LOG.debug { "Content update for lines ${update.startLine}-${update.endLine} (trim offset ${update.trimStartOffset}), the new task is $newPendingTask" }
    pendingTask = newPendingTask
  }

  private fun mergeContentUpdate(task: HighlightTask, update: TerminalOutputContentUpdate): HighlightTask {
    val taskStartLine = task.startLine.toAbsolute()
    val taskEndLine = task.endLine.toAbsolute()
    val updateStartLine = update.startLine.toAbsolute()

    // If the update starts at or before the task, it fully replaces the task.
    if (updateStartLine <= taskStartLine) {
      return HighlightTask(
        charsSequence = CharArrayUtil.createImmutableCharSequence(update.charsSequence),
        startLine = update.startLine,
        endLine = update.endLine,
        startOffset = update.startOffset,
        modificationStamp = update.modificationStamp,
      )
    }

    // The update always extends to the current end of the document,
    // so the update may start at most one line past the task's end. A larger gap means we lost an event.
    require(updateStartLine <= taskEndLine + 1) {
      "Disjoint content update: task=[$taskStartLine..$taskEndLine], update=[$updateStartLine..${update.endLine.toAbsolute()}]"
    }

    // Build merged chars:
    // - Overlap (updateStart <= taskEnd): replace [updateOffsetInTask..taskEnd) of task chars with update chars.
    // - Contiguous append (updateStart == taskEnd + 1): task chars + '\n' + update chars.
    val merged: ImmutableCharSequence = if (updateStartLine > taskEndLine) {
      task.charsSequence.concat("\n").concat(update.charsSequence)
    }
    else {
      val updateOffsetInTask = (update.startOffset - task.startOffset).toInt()
      task.charsSequence.replace(updateOffsetInTask, task.charsSequence.length, update.charsSequence)
    }

    return HighlightTask(
      charsSequence = merged,
      startLine = task.startLine,
      endLine = update.endLine,
      startOffset = task.startOffset,
      modificationStamp = update.modificationStamp,
    )
  }

  /**
   * Clamps the [task] to the trim threshold, dropping the trimmed-away prefix.
   * Returns `null` if the whole task has been trimmed away.
   */
  private fun clampHighlightTask(
    task: HighlightTask,
    trimStartLine: TerminalLineIndex,
    trimStartOffset: TerminalOffset,
  ): HighlightTask? {
    val taskStart = task.startOffset
    val taskEnd = taskStart + task.charsSequence.length.toLong()
    if (trimStartOffset <= taskStart) return task
    if (trimStartOffset >= taskEnd) return null
    // Partial overlap: drop the trimmed prefix from the task's chars and shift its startLine/startOffset.
    val charsToSkip = (trimStartOffset - taskStart).toInt()
    return task.copy(
      charsSequence = task.charsSequence.subtext(charsToSkip, task.charsSequence.length),
      startLine = trimStartLine,
      startOffset = trimStartOffset,
    )
  }

  /**
   * Drains the batches the running task has finished and rolls the task state forward.
   *
   * Returns one [TerminalHyperlinksOutputEvent.HyperlinksUpdated] per finished batch — each a self-contained update
   * for a disjoint covered range — followed by a [TerminalHyperlinksOutputEvent.TaskFinished]
   * if the currently running task has just finished.
   */
  fun collectResultsAndMaybeStartNewTask(): List<TerminalHyperlinksOutputEvent> {
    val runnerBefore = currentTaskRunner
    val events = ArrayList<TerminalHyperlinksOutputEvent>()
    if (runnerBefore != null) {
      val batches = runnerBefore.drainCompletedBatches()
      // The running task's filter must still be the current one, otherwise its results are stale and the
      // re-processing triggered by the filter change (FiltersUpdated) will recompute the whole output anyway.
      if (runnerBefore.filter === filterWrapper.getFilter()) {
        events += batches.mapNotNull { buildEvent(it) }
      }
    }

    maybeStartNewTask()
    if (runnerBefore != null && currentTaskRunner !== runnerBefore) {
      events += TerminalHyperlinksOutputEvent.TaskFinished(documentModificationStamp = runnerBefore.task.modificationStamp)
    }
    return events
  }

  /**
   * Turns a finished [batch] into an update for its covered range, clamped to the current trim threshold.
   * Returns `null` when the batch's filter is stale or its whole range has already been trimmed away.
   */
  private fun buildEvent(batch: BatchResult): TerminalHyperlinksOutputEvent.HyperlinksUpdated? {
    val trimStart = currentTrimStartOffset.toAbsolute()
    val coveredStartOffset = maxOf(batch.coveredStartOffset, trimStart)
    if (batch.coveredEndOffset <= coveredStartOffset) return null // fully trimmed away
    val links = batch.links.filter { it.absoluteStartOffset >= trimStart }
    return TerminalHyperlinksOutputEvent.HyperlinksUpdated(
      documentModificationStamp = batch.modificationStamp,
      coveredStartOffset = coveredStartOffset,
      coveredEndOffset = batch.coveredEndOffset,
      hyperlinks = links,
    )
  }

  private fun maybeStartNewTask() {
    val currentTaskRunner = currentTaskRunner
    val currentFilter = filterWrapper.getFilter()
    if (currentTaskRunner?.isRunning() == true) {
      LOG.debug { "Can't start a new task because ${currentTaskRunner.task} is still running" }
      return
    }
    val pendingBatches = currentTaskRunner?.pendingBatchCount()
    if (pendingBatches != null && pendingBatches > 0) {
      LOG.debug { "Can't start a new task because ${currentTaskRunner.task} has $pendingBatches undelivered batches" }
      return
    }
    val pending = pendingTask
    if (pending?.hasWorkToDo() != true || currentFilter == null) {
      if (currentTaskRunner != null) {
        LOG.debug {
          "Finished ${currentTaskRunner.task}, " +
          "but can't start a new one because pendingTask = $pending, currentFilter = $currentFilter"
        }
        this.currentTaskRunner = null
      }
      return
    }
    val newTaskRunner = HighlightTaskRunner(
      hyperlinkId = hyperlinkId,
      task = pending,
      filter = currentFilter,
      continueCondition = { makesSenseToContinue(it) },
    )
    currentTaskState.value = TaskState(currentTaskRunner = newTaskRunner, pendingTask = null)
  }

  private fun makesSenseToContinue(runner: HighlightTaskRunner): Boolean {
    val oldFilter = runner.filter
    val newFilter = filterWrapper.getFilter()
    if (newFilter !== oldFilter) {
      LOG.debug { "Stopping the task because the filter has changed from $oldFilter to $newFilter" }
      return false
    }
    val pendingTask = pendingTask
    // Now check whether there's a new pending task with an overlapping dirty region.
    if (pendingTask == null) {
      return true
    }
    val ourLine = runner.currentAbsoluteLine
    val nextUpdateLine = pendingTask.startLine.toAbsolute()
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
}

private data class TaskState(
  val currentTaskRunner: HighlightTaskRunner?,
  val pendingTask: HighlightTask?,
) {
  /**
   * If we have nothing to do, then both of these will be null.
   * If either is not null, it's possible that there will be no results,
   * but we may have to do some cleanup nevertheless or start a new task.
   */
  fun mayHaveWorkToDo(): Boolean = currentTaskRunner != null || pendingTask != null
}

private data class HighlightTask(
  /**
   * The text covered by this task: lines [startLine]..[endLine] (inclusive),
   * joined by `'\n'` with no trailing newline.
   * Stored as [ImmutableCharSequence] for fast `concat` / `subtext` (O(log n)) during merges.
   */
  val charsSequence: ImmutableCharSequence,
  val startLine: TerminalLineIndex,
  val endLine: TerminalLineIndex,  // Inclusive
  val startOffset: TerminalOffset,
  val modificationStamp: Long,
) {
  fun hasWorkToDo(): Boolean = endLine.toAbsolute() >= startLine.toAbsolute()

  fun absoluteOffsetOf(charsRelativeOffset: Int): Long = (startOffset + charsRelativeOffset.toLong()).toAbsolute()

  override fun toString(): String =
    "HighlightTask(startLine=$startLine, startOffset=$startOffset, endLineInclusive=$endLine, chars=${charsSequence.length})"
}

private typealias TaskResult = TerminalFilterResultInfoDto

/** A batch of lines the task has finished processing, holding the links list for its covered range. */
private data class BatchResult(
  val coveredStartOffset: Long,  // absolute, inclusive
  val coveredEndOffset: Long,    // absolute, exclusive
  val links: List<TaskResult>,
  val modificationStamp: Long,
)

private class HighlightTaskRunner(
  hyperlinkId: AtomicLong,
  val task: HighlightTask,
  val filter: CompositeFilter,
  private val continueCondition: (HighlightTaskRunner) -> Boolean,
) {
  private val isRunning = AtomicBoolean(true)

  private val processor = HyperlinkProcessor(hyperlinkId)
  private val hypertext: HypertextInput = HypertextFromCharSequenceAdapter(task.charsSequence)

  private val completedBatches = LinkedBlockingDeque<BatchResult>()

  private val topStartLine: TerminalLineIndex = task.startLine
  private val taskEnd: TerminalLineIndex = task.endLine
  private val bottomStartLine: TerminalLineIndex = (taskEnd + 1 - BATCH_SIZE).coerceAtLeast(topStartLine)
  private val topStopLineInclusive: TerminalLineIndex = bottomStartLine - 1
  private val bottomStopLineInclusive: TerminalLineIndex = taskEnd

  var currentAbsoluteLine: Long = topStartLine.toAbsolute()

  fun isRunning(): Boolean = isRunning.get()

  fun pendingBatchCount(): Int = completedBatches.size

  private operator fun TerminalLineIndex.plus(count: Int) = TerminalLineIndex.of(toAbsolute() + count)
  private operator fun TerminalLineIndex.minus(count: Int) = TerminalLineIndex.of(toAbsolute() - count)

  suspend fun run() {
    try {
      LOG.debug {
        "Started the task $task, will process lines $topStartLine-$topStopLineInclusive at the top " +
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
    val results = processor.processBatch(hypertext, task, filter, bottomStartLine, bottomStopLineInclusive)
    LOG.debug { "Produced at the bottom: ${describe(results)} in lines $bottomStartLine-$bottomStopLineInclusive" }
    enqueueCompletedBatch(bottomStartLine, bottomStopLineInclusive, results)
  }

  private suspend fun computeTopResults() {
    var firstBatchLine = topStartLine
    while (firstBatchLine <= topStopLineInclusive && continueCondition(this)) {
      val lastBatchLine = (firstBatchLine + BATCH_SIZE - 1).coerceAtMost(topStopLineInclusive)
      val results = processor.processBatch(hypertext, task, filter, firstBatchLine, lastBatchLine)
      LOG.debug { "Produced at the top: ${describe(results)} in lines $firstBatchLine-$lastBatchLine" }
      enqueueCompletedBatch(firstBatchLine, lastBatchLine, results)
      firstBatchLine = lastBatchLine + 1
      currentAbsoluteLine = firstBatchLine.toAbsolute()
    }
  }

  private fun enqueueCompletedBatch(startLine: TerminalLineIndex, endLineInclusive: TerminalLineIndex, links: List<TaskResult>) {
    if (endLineInclusive.toAbsolute() < startLine.toAbsolute()) return
    val relativeStartLine = (startLine.toAbsolute() - task.startLine.toAbsolute()).toInt()
    val coveredStartOffset = task.absoluteOffsetOf(hypertext.getLineStartOffset(relativeStartLine))
    val coveredEndOffset = if (endLineInclusive.toAbsolute() >= taskEnd.toAbsolute()) {
      task.absoluteOffsetOf(task.charsSequence.length) // the batch reaches the end of the task text
    }
    else {
      val relativeNextLine = (endLineInclusive.toAbsolute() + 1 - task.startLine.toAbsolute()).toInt()
      task.absoluteOffsetOf(hypertext.getLineStartOffset(relativeNextLine)) // start of the line after the batch
    }
    completedBatches += BatchResult(coveredStartOffset, coveredEndOffset, links, task.modificationStamp)
  }

  fun drainCompletedBatches(): List<BatchResult> {
    val batches = ArrayList<BatchResult>(completedBatches.size)
    while (true) {
      batches += completedBatches.pollFirst() ?: break
    }
    return batches
  }

  private fun describe(results: List<TaskResult>) = buildString {
    if (results.isEmpty()) {
      append("no results")
      return@buildString
    }
    append(results.size).append(" results with offsets ")
    val minOffset = TerminalOffset.of(results.minOf { it.absoluteStartOffset })
    val maxOffset = TerminalOffset.of(results.maxOf { it.absoluteEndOffset })
    append(minOffset).append("-").append(maxOffset)
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
    hypertext: HypertextInput,
    task: HighlightTask,
    filter: CompositeFilter,
    startLine: TerminalLineIndex,
    endLine: TerminalLineIndex,
  ): List<TerminalFilterResultInfoDto> =
    readAction {
      mutableListOf<TerminalFilterResultInfoDto>().also { results ->
        val relativeStart = (startLine.toAbsolute() - task.startLine.toAbsolute()).toInt()
        val relativeEnd = (endLine.toAbsolute() - task.startLine.toAbsolute()).toInt()
        filter.applyToLineRange(hypertext, relativeStart, relativeEnd) { applyResult ->
          checkCanceled()
          val hyperlinks = applyResult.filterResult?.resultItems?.flatMap { createHyperlinkOrHighlighting(task, it) } ?: emptyList()
          results.addAll(hyperlinks)
        }
      }
    }

  private fun createHyperlinkOrHighlighting(
    task: HighlightTask,
    resultItem: Filter.ResultItem,
  ): List<TerminalFilterResultInfoDto> {
    val hyperlinkInfo = resultItem.hyperlinkInfo
    val highlightAttributes = resultItem.highlightAttributes
    val notInlayResult = when {
      hyperlinkInfo != null -> TerminalHyperlinkInfoDto(
        id = TerminalHyperlinkId(hyperlinkId.incrementAndGet()),
        hyperlinkInfo = hyperlinkInfo,
        absoluteStartOffset = task.absoluteOffsetOf(resultItem.highlightStartOffset),
        absoluteEndOffset = task.absoluteOffsetOf(resultItem.highlightEndOffset),
        style = highlightAttributes?.toDto(),
        followedStyle = resultItem.followedHyperlinkAttributes?.toDto(),
        hoveredStyle = resultItem.hoveredHyperlinkAttributes?.toDto(),
        isInvisibleLink = resultItem.isInvisibleLink,
        layer = resultItem.highlighterLayer,
      )
      highlightAttributes != null -> TerminalHighlightingInfoDto(
        id = TerminalHyperlinkId(hyperlinkId.incrementAndGet()),
        absoluteStartOffset = task.absoluteOffsetOf(resultItem.highlightStartOffset),
        absoluteEndOffset = task.absoluteOffsetOf(resultItem.highlightEndOffset),
        style = highlightAttributes.toDto(),
        layer = resultItem.highlighterLayer,
      )
      else -> null
    }
    val inlayResult = (resultItem as? InlayProvider)?.let { inlayProvider ->
      TerminalInlayInfoDto(
        id = TerminalHyperlinkId(hyperlinkId.incrementAndGet()),
        absoluteStartOffset = task.absoluteOffsetOf(resultItem.highlightStartOffset),
        absoluteEndOffset = task.absoluteOffsetOf(resultItem.highlightEndOffset),
        inlayProvider = inlayProvider,
      )
    }
    return listOfNotNull(notInlayResult, inlayResult)
  }
}

private class HypertextFromCharSequenceAdapter(private val chars: CharSequence) : HypertextInput {
  private val lineStartOffsets: IntArray = run {
    val lineCount = chars.count { it == '\n' } + 1
    val starts = IntArray(lineCount)
    var idx = 1
    chars.forEachIndexed { i, c ->
      if (c == '\n') {
        starts[idx++] = i + 1
      }
    }
    starts
  }

  override val lineCount: Int
    get() = lineStartOffsets.size

  override fun getLineStartOffset(lineIndex: Int): Int = lineStartOffsets[lineIndex]

  override fun getLineText(lineIndex: Int): String {
    val start = lineStartOffsets[lineIndex]
    return if (lineIndex + 1 < lineStartOffsets.size) {
      val end = lineStartOffsets[lineIndex + 1]
      chars.subSequence(start, end).toString()  // with line break at the end
    }
    else {
      chars.subSequence(start, chars.length).toString() + "\n"
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