// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException
import org.jetbrains.idea.maven.utils.MavenProgressIndicator

abstract class MavenEmbedderWrapperEx(project: Project) : MavenEmbedderWrapper(project) {
  @Throws(MavenProcessCanceledException::class)
  override fun resolve(longRunningTaskId: String,
                       requests: MutableCollection<MavenArtifactResolutionRequest>,
                       progressIndicator: MavenProgressIndicator?): MutableList<MavenArtifact> =
    runBlocking {
      val embedder = getOrCreateWrappee()

      val progressIndication = launch {
        while (isActive) {
          delay(1000)
          if (null != progressIndicator) {
            val status = embedder.getLongRunningTaskStatus(longRunningTaskId, ourToken)
            progressIndicator.setFraction(status.fraction())
            if (progressIndicator.isCanceled) {
              if (embedder.cancelLongRunningTask(longRunningTaskId, ourToken)) {
                break;
              }
            }
          }
        }
      }

      val result = async {
        try {
          return@async withContext(Dispatchers.IO) {
            return@withContext embedder.resolve(longRunningTaskId, requests, ourToken)
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