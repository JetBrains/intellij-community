// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole
import org.jetbrains.idea.maven.project.MavenConsole
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException
import java.util.*

abstract class MavenEmbedderWrapperEx(project: Project) : MavenEmbedderWrapper(project) {
  @Throws(MavenProcessCanceledException::class)
  override fun <R> runLongRunningTask(task: LongRunningEmbedderTask<R>,
                                      indicator: ProgressIndicator?,
                                      syncConsole: MavenSyncConsole?,
                                      console: MavenConsole?): R {
    val longRunningTaskId = UUID.randomUUID().toString()
    val embedder = getOrCreateWrappee()

    return runLongRunningTask(embedder, longRunningTaskId, task, indicator, syncConsole, console)
  }

  private fun <R> runLongRunningTask(embedder: MavenServerEmbedder,
                                     longRunningTaskId: String,
                                     task: LongRunningEmbedderTask<R>,
                                     indicator: ProgressIndicator?,
                                     syncConsole: MavenSyncConsole?,
                                     console: MavenConsole?): R {
    @Suppress("RAW_RUN_BLOCKING")
    return runBlocking {
      return@runBlocking runLongRunningTaskAsync(embedder, longRunningTaskId, indicator, syncConsole, console, task)
    }
  }

  private suspend fun <R> runLongRunningTaskAsync(embedder: MavenServerEmbedder,
                                                  longRunningTaskId: String,
                                                  indicator: ProgressIndicator?,
                                                  syncConsole: MavenSyncConsole?,
                                                  console: MavenConsole?,
                                                  task: LongRunningEmbedderTask<R>): R {
    return coroutineScope {
      val progressIndication = launch {
        while (isActive) {
          delay(500)
          val status = embedder.getLongRunningTaskStatus(longRunningTaskId, ourToken)
          console?.handleConsoleEvents(status.consoleEvents())
          indicator?.fraction = status.fraction()
          syncConsole?.handleDownloadEvents(status.downloadEvents())
          if (null != indicator && indicator.isCanceled) {
            if (embedder.cancelLongRunningTask(longRunningTaskId, ourToken)) {
              throw MavenProcessCanceledException()
            }
          }
        }
      }

      val result = async {
        try {
          return@async withContext(Dispatchers.IO) {
            return@withContext task.run(embedder, longRunningTaskId)
          }
        }
        catch (e: Exception) {
          throw MavenProcessCanceledException(e)
        }
        finally {
          progressIndication.cancel()
        }
      }
      return@coroutineScope result.await()
    }
  }
}