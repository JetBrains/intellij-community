package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.Disposer
import com.intellij.vcs.log.data.index.*
import com.intellij.vcs.log.impl.VcsLogNavigationUtil.waitForRefresh
import com.intellij.vcs.log.impl.VcsProjectLog.Companion.getInstance
import com.jetbrains.performancePlugin.utils.TimeArgumentParserUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Command waits for finishing of git log indexing process
 * Example of infinity waiting                                    - %waitVcsLogIndexing
 * Example of timed waiting with throwing exception on expiration - %waitVcsLogIndexing 5s
 */
class WaitVcsLogIndexingCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "waitVcsLogIndexing"
    const val PREFIX = CMD_PREFIX + NAME
    private val LOG = logger<WaitVcsLogIndexingCommand>()
  }

  override suspend fun doExecute(context: PlaybackContext) {
    LOG.info("$NAME command started its execution")

    val logManager = getInstance(context.project).logManager ?: return
    withContext(Dispatchers.EDT) {
      if (!logManager.isLogUpToDate) logManager.waitForRefresh()
    }

    val vcsIndex = logManager.dataManager.index
    LOG.info("Need indexing = ${vcsIndex.needIndexing()}, " +
             "is indexing scheduled = ${vcsIndex.isIndexingScheduled()}, " +
             "is indexing paused = ${vcsIndex.isIndexingPaused()}")

    if (vcsIndex.isIndexingScheduled()) {
      val bigRepositoriesList = VcsLogBigRepositoriesList.getInstance()
      val listenersDisposable = Disposer.newDisposable("Vcs Log Indexing Listeners")

      val isIndexingCompleted = CompletableDeferred<Boolean>()

      val indexingListener = VcsLogIndex.IndexingFinishedListener { _ ->
        logIndexingCompleted("indexing was finished after ${VcsLogIndex.IndexingFinishedListener::class.simpleName} invocation")
        isIndexingCompleted.complete(true)
      }
      vcsIndex.addListener(indexingListener)
      Disposer.register(listenersDisposable, Disposable { vcsIndex.removeListener(indexingListener) })

      bigRepositoriesList.addListener(VcsLogBigRepositoriesList.Adapter {
        logIndexingCompleted("indexing was paused after ${VcsLogBigRepositoriesList.Listener::class.simpleName} invocation")
        isIndexingCompleted.complete(true)
      }, listenersDisposable)

      isIndexingCompleted.invokeOnCompletion { Disposer.dispose(listenersDisposable) }

      // second check to ensure that indexing was not completed after the first check and before adding listeners
      if (!vcsIndex.isIndexingScheduled()) {
        logIndexingCompleted(if (vcsIndex.isIndexingPaused()) "indexing was paused" else "indexing was completed")
        isIndexingCompleted.complete(true)
      }
      else {
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
    }
    vcsIndex.indexingRoots.forEach { LOG.info("Status of git root ${it.name}, indexed = ${vcsIndex.isIndexed(it)}") }
  }

  private fun logIndexingCompleted(reason: String) = LOG.info("$NAME command was completed because $reason")

  override fun getName(): String = NAME

}