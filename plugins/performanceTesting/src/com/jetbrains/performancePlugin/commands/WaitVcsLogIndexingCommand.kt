package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.vcs.log.data.index.VcsLogModifiableIndex
import com.intellij.vcs.log.data.index.needIndexing
import com.intellij.vcs.log.impl.VcsProjectLog.Companion.getInstance
import com.jetbrains.performancePlugin.utils.TimeArgumentParserUtil
import kotlinx.coroutines.CompletableDeferred

/**
 * Command for waiting finishing of git log indexing process
 * Example - %waitVcsLogIndexing 5s
 */
class WaitVcsLogIndexingCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "waitVcsLogIndexing"
    const val PREFIX = CMD_PREFIX + NAME
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val logManager = getInstance(context.project).logManager ?: return
    val dataManager = logManager.dataManager
    val vcsIndex = dataManager.index as VcsLogModifiableIndex

    if (vcsIndex.needIndexing()) {
      val args = extractCommandArgument(PREFIX);
      val isIndexingCompleted = CompletableDeferred<Boolean>()
      vcsIndex.addListener { _ -> isIndexingCompleted.complete(true) }

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
  }

  override fun getName(): String = NAME

}