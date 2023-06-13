package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.vcs.log.data.index.VcsLogModifiableIndex
import com.intellij.vcs.log.data.index.isIndexingPaused
import com.intellij.vcs.log.data.index.needIndexing
import com.intellij.vcs.log.impl.VcsProjectLog.Companion.getInstance
import com.jetbrains.performancePlugin.utils.TimeArgumentParserUtil
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Command waits for finishing of git log indexing process
 * Example - %waitVcsLogIndexing
 * Example - %waitVcsLogIndexing 5s
 */
class WaitVcsLogIndexingCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "waitVcsLogIndexing"
    const val PREFIX = CMD_PREFIX + NAME
  }

  private val executor: ScheduledExecutorService = AppExecutorUtil.getAppScheduledExecutorService()

  override suspend fun doExecute(context: PlaybackContext) {
    val logManager = getInstance(context.project).logManager ?: return
    val dataManager = logManager.dataManager
    val vcsIndex = dataManager.index as VcsLogModifiableIndex

    if (vcsIndex.needIndexing()) {
      val isIndexingCompleted = CompletableDeferred<Boolean>()
      vcsIndex.addListener { _ -> isIndexingCompleted.complete(true) }
      val indexPauseTask = buildIndexPauseTask(vcsIndex, isIndexingCompleted)
      try {
        val args = extractCommandArgument(PREFIX)
        //Will wait infinitely while test execution timeout won't be occurred
        if (args.isBlank()) {
          isIndexingCompleted.await()
        }
        //Will wait for specified condition and fail with exception in case when condition wasn't satisfied
        else {
          val (timeout, timeunit) = TimeArgumentParserUtil.parse(args)
          Waiter.waitOrThrow(timeout, timeunit, "Git log indexing project wasn't finished in $timeout $timeunit") {
            isIndexingCompleted.await()
          }
        }
      }
      finally {
        indexPauseTask.cancel(false)
      }
    }

  }

  /**
   * Polling of the property isIndexingPaused. This task will detect the case when indexing wasn't fully completed
   * due to timeout of 20 minutes was reached
   */
  private fun buildIndexPauseTask(vscIndex: VcsLogModifiableIndex, isIndexingCompleted: CompletableDeferred<Boolean>): ScheduledFuture<*> {
    return executor.scheduleWithFixedDelay(
      {
        if (vscIndex.isIndexingPaused()) {
          isIndexingCompleted.complete(true)
          return@scheduleWithFixedDelay
        }
      }, 0, 10, TimeUnit.SECONDS)
  }

  override fun getName(): String = NAME

}