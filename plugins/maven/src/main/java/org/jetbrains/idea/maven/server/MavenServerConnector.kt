// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.Cancellation.ensureActive
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.messages.Topic
import kotlinx.coroutines.delay
import org.jetbrains.annotations.ApiStatus
import java.rmi.RemoteException

interface MavenServerConnector : Disposable {
  val supportType: String

  val state: State

  val jdk: Sdk

  val mavenDistribution: MavenDistribution

  val vmOptions: String

  val project: Project?

  val multimoduleDirectories: List<String>

  @ApiStatus.Internal
  fun isNew(): Boolean

  @ApiStatus.Internal
  fun connect()

  @ApiStatus.Internal
  @Deprecated("use suspend", ReplaceWith("ping"))
  fun pingBlocking(): Boolean

  @ApiStatus.Internal
  suspend fun ping(): Boolean

  @ApiStatus.Internal
  fun stop(wait: Boolean)

  @ApiStatus.Internal
  fun getDebugStatus(clean: Boolean): MavenServerStatus

  fun addMultimoduleDir(multimoduleDirectory: String): Boolean

  @ApiStatus.Internal
  suspend fun createEmbedder(settings: MavenEmbedderSettings): MavenServerEmbedder

  @Throws(RemoteException::class)
  fun createIndexer(): MavenServerIndexer

  fun checkConnected(): Boolean

  enum class State {
    STARTING,
    RUNNING,
    FAILED,
    STOPPED
  }

  companion object {
    @JvmField
    @Topic.AppLevel
    val DOWNLOAD_LISTENER_TOPIC: Topic<MavenServerDownloadListener> =
      Topic(MavenServerDownloadListener::class.java.simpleName, MavenServerDownloadListener::class.java)

    private suspend fun <T> retry(action: suspend () -> T): T {
      for (i in 1..3) {
        try {
          return action()

        }
        catch (e: Throwable) {
          ensureActive()
          if (i == 3) throw e;
          delay(100L * i)
        }
      }
      throw IllegalStateException()
    }

  }
}
