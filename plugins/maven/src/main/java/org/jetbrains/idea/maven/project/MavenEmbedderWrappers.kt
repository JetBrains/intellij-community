// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.importing.MavenImportUtil.guessExistingEmbedderDir
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper
import org.jetbrains.idea.maven.server.MavenServerManager
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
interface MavenEmbedderWrappers : AutoCloseable {
  fun getEmbedder(baseDir: String): MavenEmbedderWrapper = getEmbedder(Path.of(baseDir))
  fun getAlwaysOnlineEmbedder(baseDir: String): MavenEmbedderWrapper
  fun getEmbedder(baseDir: Path): MavenEmbedderWrapper
}

internal class MavenEmbedderWrappersImpl(private val myProject: Project) : MavenEmbedderWrappers {
  private val myEmbedders = ConcurrentHashMap<Path, MavenEmbedderWrapper>()

  override fun getAlwaysOnlineEmbedder(baseDir: String) = getEmbedder(Path.of(baseDir), true)

  override fun getEmbedder(baseDir: Path) = getEmbedder(baseDir, false)

  private fun getEmbedder(baseDir: Path, alwaysOnline: Boolean): MavenEmbedderWrapper {
    val embedderDir = guessExistingEmbedderDir(myProject, baseDir.toString())
    return myEmbedders.computeIfAbsent(baseDir) {
      MavenServerManager.getInstance().createEmbedder(myProject, alwaysOnline, embedderDir)
    }
  }

  override fun close() {
    myEmbedders.values.forEach { it.release() }
    myEmbedders.clear()
  }
}