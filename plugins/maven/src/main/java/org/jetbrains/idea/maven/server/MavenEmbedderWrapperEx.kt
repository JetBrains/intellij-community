// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import org.jetbrains.idea.maven.project.MavenConsole
import org.jetbrains.idea.maven.server.MavenArtifactEvent.ArtifactEventType
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import java.util.*

abstract class MavenEmbedderWrapperEx(project: Project) : MavenEmbedderWrapper(project) {
  @Throws(MavenProcessCanceledException::class)
  override fun <R> runLongRunningTask(task: LongRunningEmbedderTask<R>,
                                      indicator: MavenProgressIndicator?,
                                      console: MavenConsole?): R =
    runBlocking {
      val longRunningTaskId = UUID.randomUUID().toString()
      val embedder = getOrCreateWrappee()

      val progressIndication = launch {
        if (null != indicator) {
          while (isActive) {
            delay(500)
            val status = embedder.getLongRunningTaskStatus(longRunningTaskId, ourToken)
            indicator.setFraction(status.fraction())
            for (e in status.downloadEvents()) {
              when (e.artifactEventType) {
                ArtifactEventType.DOWNLOAD_STARTED -> indicator.startedDownload(e.resolveType, e.dependencyId)
                ArtifactEventType.DOWNLOAD_COMPLETED -> indicator.completedDownload(e.resolveType, e.dependencyId)
                ArtifactEventType.DOWNLOAD_FAILED -> indicator.failedDownload(e.resolveType, e.dependencyId, e.errorMessage, e.stackTrace)
              }
            }
            if (null != console) {
              for (e in status.consoleEvents()) {
                console.printMessage(e.level, e.message, e.throwable)
              }
            }
            if (indicator.isCanceled) {
              if (embedder.cancelLongRunningTask(longRunningTaskId, ourToken)) {
                break
              }
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

      return@runBlocking result.await()
    }
}