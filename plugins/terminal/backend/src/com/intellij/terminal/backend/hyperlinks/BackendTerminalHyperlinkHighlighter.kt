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
import java.util.Deque
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
   * Used by [isValid] to drop results whose offsets fall in a region trimmed after the running task started.
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

  // If we have nothing to do, then both of these will be null.
  // If either is not null, it's possible that there will be no results,
  // but we may have to do some cleanup nevertheless or start a new task.
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
   * Drains the next batch of hyperlink results and rolls the task state forward.
   *
   * Returns 0, 1, or 2 events:
   * - At most one [TerminalHyperlinksOutputEvent.HyperlinksUpdated] carrying newly computed results.
   * - At most one [TerminalHyperlinksOutputEvent.TaskFinished], emitted if the currently running task has finished.
   *   When both events fire in the same call, `TaskFinished` comes after the `HyperlinksUpdated` in the returned list.
   */
  fun collectResultsAndMaybeStartNewTask(): List<TerminalHyperlinksOutputEvent> {
    val runnerBefore = currentTaskRunner
    val hyperlinksEvent = runnerBefore?.getNextOutputEvent { isValid(it) }

    maybeStartNewTask()
    val taskFinished = if (runnerBefore != null && currentTaskRunner !== runnerBefore) {
      TerminalHyperlinksOutputEvent.TaskFinished(documentModificationStamp = runnerBefore.task.modificationStamp)
    }
    else null
    return listOfNotNull(hyperlinksEvent, taskFinished)
  }

  private fun isValid(taskResult: TaskResult): Boolean {
    val currentFilter = filterWrapper.getFilter()
    val currentTaskRunner = checkNotNull(currentTaskRunner) { "The task runner must be present since we have results" }
    if (currentTaskRunner.filter !== currentFilter) return false
    if (TerminalOffset.of(taskResult.absoluteStartOffset) < currentTrimStartOffset) return false // trimmed
    val pendingTask = pendingTask
    return if (pendingTask == null) {
      true // No updates since the current task started, therefore, all results are valid
    }
    else {
      taskResult.absoluteEndOffset <= pendingTask.startAbsoluteOffset
    }
  }

  private fun maybeStartNewTask() {
    val currentTaskRunner = currentTaskRunner
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
  val startAbsoluteOffset: Long get() = startOffset.toAbsolute()

  fun hasWorkToDo(): Boolean = endLine.toAbsolute() >= startLine.toAbsolute()

  override fun toString(): String =
    "HighlightTask(startLine=$startLine, startOffset=$startOffset, endLineInclusive=$endLine, chars=${charsSequence.length})"
}

private typealias TaskResult = TerminalFilterResultInfoDto

private class HighlightTaskRunner(
  hyperlinkId: AtomicLong,
  val task: HighlightTask,
  val filter: CompositeFilter,
  private val continueCondition: (HighlightTaskRunner) -> Boolean,
) {
  private val isRunning = AtomicBoolean(true)
  private var isFirstEvent = true

  private val processor = HyperlinkProcessor(hyperlinkId)
  private val hypertext: HypertextInput = HypertextFromCharSequenceAdapter(task.charsSequence)

  val topResults = LinkedBlockingDeque<TaskResult>()
  val bottomResults = LinkedBlockingDeque<TaskResult>()

  private val topStartLine: TerminalLineIndex = task.startLine
  private val taskEnd: TerminalLineIndex = task.endLine
  private val bottomStartLine: TerminalLineIndex = (taskEnd + 1 - BATCH_SIZE).coerceAtLeast(topStartLine)
  private val topStopLineInclusive: TerminalLineIndex = bottomStartLine - 1
  private val bottomStopLineInclusive: TerminalLineIndex = taskEnd

  var currentAbsoluteLine: Long = topStartLine.toAbsolute()

  fun isRunning(): Boolean = isRunning.get()

  fun resultsCount(): Int = topResults.size + bottomResults.size

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
    bottomResults += results
  }

  private suspend fun computeTopResults() {
    var firstBatchLine = topStartLine
    while (firstBatchLine <= topStopLineInclusive && continueCondition(this)) {
      val lastBatchLine = (firstBatchLine + BATCH_SIZE - 1).coerceAtMost(topStopLineInclusive)
      val results = processor.processBatch(hypertext, task, filter, firstBatchLine, lastBatchLine)
      topResults += results
      LOG.debug { "Produced at the top: ${describe(results)} in lines $firstBatchLine-$lastBatchLine" }
      firstBatchLine = lastBatchLine + 1
      currentAbsoluteLine = firstBatchLine.toAbsolute()
    }
  }

  fun getNextOutputEvent(predicate: (TaskResult) -> Boolean): TerminalHyperlinksOutputEvent? {
    return createEvent(collectResults(predicate))
  }

  private fun collectResults(predicate: (TaskResult) -> Boolean): List<TaskResult> {
    val results = ArrayList<TaskResult>(topResults.size + bottomResults.size)
    collectValidAndRemoveInvalidResults(topResults, results, predicate)
    collectValidAndRemoveInvalidResults(bottomResults, results, predicate)
    LOG.debug { "Got ${describe(results)} from the task $task" }
    return results
  }

  private fun collectValidAndRemoveInvalidResults(
    from: Deque<TaskResult>,
    to: MutableList<TaskResult>,
    predicate: (TaskResult) -> Boolean,
  ) {
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

  private fun createEvent(hyperlinks: List<TaskResult>): TerminalHyperlinksOutputEvent? {
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
    return TerminalHyperlinksOutputEvent.HyperlinksUpdated(
      documentModificationStamp = task.modificationStamp,
      removeFromOffset = if (remove) task.startAbsoluteOffset else null,
      hyperlinks = hyperlinks,
    )
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

  private fun HighlightTask.absoluteOffsetOf(charsRelativeOffset: Int): Long =
    (startOffset + charsRelativeOffset.toLong()).toAbsolute()
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