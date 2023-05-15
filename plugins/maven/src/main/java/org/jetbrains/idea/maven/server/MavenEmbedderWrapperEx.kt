// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import org.jetbrains.idea.maven.project.MavenConsole
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import java.util.*

abstract class MavenEmbedderWrapperEx(project: Project) : MavenEmbedderWrapper(project) {
  @Throws(MavenProcessCanceledException::class)
  override fun <R> runLongRunningTask(task: LongRunningEmbedderTask<R>,
                                      indicator: MavenProgressIndicator?,
                                      console: MavenConsole?): R {
    val longRunningTaskId = UUID.randomUUID().toString()
    val embedder = getOrCreateWrappee()

    val mavenIndicator = indicator ?: MavenProgressIndicator(null, null)

    return ProgressManager.getInstance().computeResultUnderProgress(
      { runLongRunningTask(embedder, longRunningTaskId, task, mavenIndicator, console) }, mavenIndicator.indicator)
  }

  private fun <R> runLongRunningTask(embedder: MavenServerEmbedder,
                                     longRunningTaskId: String,
                                     task: LongRunningEmbedderTask<R>,
                                     indicator: MavenProgressIndicator,
                                     console: MavenConsole?): R {
    return runBlockingCancellable {
      return@runBlockingCancellable runLongRunningTaskAsync(embedder, longRunningTaskId, indicator, console, task)
    }
  }

  private suspend fun <R> runLongRunningTaskAsync(embedder: MavenServerEmbedder,
                                                  longRunningTaskId: String,
                                                  indicator: MavenProgressIndicator,
                                                  console: MavenConsole?,
                                                  task: LongRunningEmbedderTask<R>): R {
    return coroutineScope {
      val progressIndication = launch {
        while (isActive) {
          delay(500)
          val status = embedder.getLongRunningTaskStatus(longRunningTaskId, ourToken)
          console?.handleConsoleEvents(status.consoleEvents())
          indicator.setFraction(status.fraction())
          indicator.handleDownloadEvents(status.downloadEvents())
          if (indicator.isCanceled) {
            if (embedder.cancelLongRunningTask(longRunningTaskId, ourToken)) {
              break
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