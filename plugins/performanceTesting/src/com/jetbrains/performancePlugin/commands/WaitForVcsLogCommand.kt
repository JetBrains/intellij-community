package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.vcs.log.data.index.VcsLogModifiableIndex
import com.intellij.vcs.log.data.index.isIndexingPaused
import com.intellij.vcs.log.data.index.needIndexing
import com.intellij.vcs.log.impl.VcsProjectLog.Companion.getInstance
import com.jetbrains.performancePlugin.utils.TimeArgumentHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.withTimeoutOrNull
import java.time.Duration
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Command for waiting finishing of git log indexing process
 * Example - %waitForGitLogIndexing 5s
 */
class WaitForVcsLogCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "waitForGitLogIndexing"
    const val PREFIX = CMD_PREFIX + NAME
  }

  override suspend fun doExecute(context: PlaybackContext) {
    DumbService.getInstance(context.project).waitForSmartMode()

    val logManager = getInstance(context.project).logManager ?: return
    val dataManager = logManager.dataManager
    val modifiableIndex = dataManager.index as VcsLogModifiableIndex

    val (timeout, timeunit) = TimeArgumentHelper.parse(extractCommandArgument(PREFIX))
    var pauseVerifier: ScheduledFuture<*>? = null
    try {
      //Schedule a task that will check if indexing was paused and trying to resume it
      pauseVerifier = AppExecutorUtil
        .getAppScheduledExecutorService()
        .scheduleWithFixedDelay(
          {
            if (modifiableIndex.needIndexing() && modifiableIndex.isIndexingPaused()) {
              CoroutineScope(Dispatchers.EDT).launch {
                //modifiableIndex.toggleIndexing()
              }
            }
          },
          0, 2, TimeUnit.MILLISECONDS
        )

      val isIndexingCompleted = CompletableDeferred<Boolean>()
      modifiableIndex.addListener { _ -> isIndexingCompleted.complete(true) }

      withTimeoutOrNull(Duration.of(timeout, timeunit)) { isIndexingCompleted.await() }
      ?: throw RuntimeException("Git log indexing project wasn't finished in $timeout $timeunit")
    }
    finally {
      pauseVerifier?.cancel(true)
    }
  }

  override fun getName(): String {
    return PREFIX
  }
}