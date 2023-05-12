// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import java.util.*

abstract class MavenEmbedderWrapperEx(project: Project) : MavenEmbedderWrapper(project) {
  @Throws(MavenProcessCanceledException::class)
  override fun <R> runLongRunningTask(task: LongRunningEmbedderTask<R>,
                                      progressIndicator: MavenProgressIndicator?): R =
    runBlocking {
      val longRunningTaskId = UUID.randomUUID().toString()
      val embedder = getOrCreateWrappee()

      val progressIndication = launch {
        if (null != progressIndicator) {
          while (isActive) {
            delay(1000)
            val status = embedder.getLongRunningTaskStatus(longRunningTaskId, ourToken)
            progressIndicator.setFraction(status.fraction())
            if (progressIndicator.isCanceled) {
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