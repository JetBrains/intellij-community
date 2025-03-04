// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Pair
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.idea.maven.importing.MavenImportUtil.guessExistingEmbedderDir
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil.getBaseDir

class MavenEmbeddersManager(private val project: Project) {
  private val myPool: MutableMap<Pair<Key<*>, String>, MavenEmbedderWrapper> = ContainerUtil.createSoftValueMap<Pair<Key<*>, String>, MavenEmbedderWrapper>()
  private val myEmbeddersInUse: MutableSet<MavenEmbedderWrapper> = HashSet<MavenEmbedderWrapper>()

  @Synchronized
  fun reset() {
    releasePooledEmbedders(false)
  }

  // used in third-party plugins
  @Synchronized
  fun getEmbedder(mavenProject: MavenProject, kind: Key<*>): MavenEmbedderWrapper {
    val baseDir = getBaseDir(mavenProject.directoryFile).toString()
    return getEmbedder(kind, baseDir)
  }

  @Synchronized
  fun getEmbedder(kind: Key<*>, multiModuleProjectDirectory: String): MavenEmbedderWrapper {
    val embedderDir = guessExistingEmbedderDir(project, multiModuleProjectDirectory)

    val key = Pair.create<Key<*>, String>(kind, embedderDir)
    var result = myPool[key]
    val alwaysOnline = kind === FOR_DOWNLOAD

    if (result != null && !result.isCompatibleWith(project, multiModuleProjectDirectory)) {
      myPool.remove(key)
      myEmbeddersInUse.remove(result)
      result = null
    }

    if (result == null) {
      result = createEmbedder(embedderDir, alwaysOnline)
      myPool[key] = result
    }

    if (myEmbeddersInUse.contains(result)) {
      MavenLog.LOG.warn("embedder $key is already used")
      return createEmbedder(embedderDir, alwaysOnline)
    }

    myEmbeddersInUse.add(result)
    return result
  }

  private fun createEmbedder(multiModuleProjectDirectory: String, alwaysOnline: Boolean): MavenEmbedderWrapper {
    return MavenServerManager.getInstance().createEmbedder(project, alwaysOnline, multiModuleProjectDirectory)
  }

  @Synchronized
  fun release(embedder: MavenEmbedderWrapper) {
    if (!myEmbeddersInUse.contains(embedder)) {
      embedder.release()
      return
    }

    myEmbeddersInUse.remove(embedder)
  }

  @TestOnly
  @Synchronized
  fun releaseInTests() {
    if (!myEmbeddersInUse.isEmpty()) {
      MavenLog.LOG.warn("embedders should be release first")
    }
    releasePooledEmbedders(false)
  }

  @Synchronized
  private fun releasePooledEmbedders(includeInUse: Boolean) {
    for (each in myPool.keys) {
      val embedder = myPool[each]
      if (embedder == null) continue  // collected

      if (!includeInUse && myEmbeddersInUse.contains(embedder)) continue
      embedder.release()
    }
    myPool.clear()
    myEmbeddersInUse.clear()
  }

  companion object {
    // used in third-party plugins
    @JvmField
    val FOR_DEPENDENCIES_RESOLVE: Key<*> = Key.create<Any?>(MavenEmbeddersManager::class.java.toString() + ".FOR_DEPENDENCIES_RESOLVE")

    @JvmField
    val FOR_POST_PROCESSING: Key<*> = Key.create<Any?>(MavenEmbeddersManager::class.java.toString() + ".FOR_POST_PROCESSING")

    // will always regardless to 'work offline' setting
    @JvmField
    val FOR_DOWNLOAD: Key<*> = Key.create<Any?>(MavenEmbeddersManager::class.java.toString() + ".FOR_DOWNLOAD")
  }
}
