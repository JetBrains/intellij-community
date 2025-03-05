// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.project.Project
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper
import org.jetbrains.idea.maven.server.MavenServerManager
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
interface MavenEmbedderWrappers : AutoCloseable {
  suspend fun getEmbedder(baseDir: String): MavenEmbedderWrapper = getEmbedder(Path.of(baseDir))
  suspend fun getAlwaysOnlineEmbedder(baseDir: String): MavenEmbedderWrapper
  suspend fun getEmbedder(baseDir: Path): MavenEmbedderWrapper
}

internal class MavenEmbedderWrappersImpl(private val myProject: Project) : MavenEmbedderWrappers {
  private val mutex = Mutex()
  private val myEmbedders = ConcurrentHashMap<Path, MavenEmbedderWrapper>()

  override suspend fun getAlwaysOnlineEmbedder(baseDir: String) = getEmbedder(Path.of(baseDir), true)

  override suspend fun getEmbedder(baseDir: Path) = getEmbedder(baseDir, false)

  private suspend fun getEmbedder(baseDir: Path, alwaysOnline: Boolean): MavenEmbedderWrapper {
    val embedderDir = baseDir.toString()
    val existing = myEmbedders[baseDir]
    if (null != existing) {
      return existing
    }
    mutex.withLock {
      val existing = myEmbedders[baseDir]
      if (null != existing) {
        return existing
      }
      val newEmbedder = MavenServerManager.getInstance().createEmbedder(myProject, alwaysOnline, embedderDir)
      myEmbedders[baseDir] = newEmbedder
      return newEmbedder
    }
  }

  override fun close() {
    myEmbedders.values.forEach { it.release() }
    myEmbedders.clear()
  }
}