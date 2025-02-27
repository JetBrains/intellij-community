// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Pair
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper
import org.jetbrains.idea.maven.server.MavenServerManager.Companion.getInstance
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil.getBaseDir
import java.nio.file.Files
import java.nio.file.Path

class MavenEmbeddersManager(private val myProject: Project) {
  private val myPool: MutableMap<Pair<Key<*>, String>, MavenEmbedderWrapper> = ContainerUtil.createSoftValueMap<Pair<Key<*>, String>, MavenEmbedderWrapper>()
  private val myEmbeddersInUse: MutableSet<MavenEmbedderWrapper?> = HashSet<MavenEmbedderWrapper?>()

  @Synchronized
  fun reset() {
    releasePooledEmbedders(false)
  }

  // used in third-party plugins
  @Synchronized
  fun getEmbedder(mavenProject: MavenProject, kind: Key<*>?): MavenEmbedderWrapper {
    val baseDir = getBaseDir(mavenProject.directoryFile).toString()
    return getEmbedder(kind, baseDir)
  }

  @Synchronized
  fun getEmbedder(kind: Key<*>?, multiModuleProjectDirectory: String): MavenEmbedderWrapper {
    val embedderDir = guessExistingEmbedderDir(multiModuleProjectDirectory)

    val key = Pair.create<Key<*>, String>(kind, embedderDir)
    var result = myPool.get(key)
    val alwaysOnline = kind === FOR_DOWNLOAD

    if (result == null) {
      result = createEmbedder(embedderDir, alwaysOnline)
      myPool.put(key, result)
    }

    if (myEmbeddersInUse.contains(result)) {
      MavenLog.LOG.warn("embedder " + key + " is already used")
      return createEmbedder(embedderDir, alwaysOnline)
    }

    myEmbeddersInUse.add(result)
    return result
  }

  private fun guessExistingEmbedderDir(multiModuleProjectDirectory: String): String {
    var dir: String? = multiModuleProjectDirectory
    if (dir!!.isBlank()) {
      MavenLog.LOG.warn("Maven project directory is blank. Using project base path")
      dir = myProject.getBasePath()
    }
    if (null == dir || dir.isBlank()) {
      MavenLog.LOG.warn("Maven project directory is blank. Using tmp dir")
      dir = System.getProperty("java.io.tmpdir")
    }
    val originalPath = Path.of(dir).toAbsolutePath()
    var path: Path? = originalPath
    while (null != path && !Files.exists(path)) {
      MavenLog.LOG.warn(String.format("Maven project %s directory does not exist. Using parent", path))
      path = path.getParent()
    }
    if (null == path) {
      MavenLog.LOG.warn("Could not determine maven project directory: " + multiModuleProjectDirectory)
      return originalPath.toString()
    }
    return path.toString()
  }

  private fun createEmbedder(multiModuleProjectDirectory: String, alwaysOnline: Boolean): MavenEmbedderWrapper {
    return getInstance().createEmbedder(myProject, alwaysOnline, multiModuleProjectDirectory)
  }

  // used in third-party plugins
  @Deprecated("use {@link MavenEmbeddersManager#getEmbedder(Key, String)} instead")
  @Synchronized
  fun getEmbedder(kind: Key<*>, ignoredWorkingDirectory: String?, multiModuleProjectDirectory: String): MavenEmbedderWrapper {
    return getEmbedder(kind, multiModuleProjectDirectory)
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
  fun releaseForcefullyInTests() {
    releasePooledEmbedders(true)
  }

  @Synchronized
  private fun releasePooledEmbedders(includeInUse: Boolean) {
    for (each in myPool.keys) {
      val embedder = myPool.get(each)
      if (embedder == null) continue  // collected

      if (!includeInUse && myEmbeddersInUse.contains(embedder)) continue
      embedder.release()
    }
    myPool.clear()
    myEmbeddersInUse.clear()
  }

  companion object {
    @JvmField
    val FOR_DEPENDENCIES_RESOLVE: Key<*> = Key.create<Any?>(MavenEmbeddersManager::class.java.toString() + ".FOR_DEPENDENCIES_RESOLVE")
    @JvmField
    val FOR_PLUGINS_RESOLVE: Key<*> = Key.create<Any?>(MavenEmbeddersManager::class.java.toString() + ".FOR_PLUGINS_RESOLVE")
    @JvmField
    val FOR_FOLDERS_RESOLVE: Key<*> = Key.create<Any?>(MavenEmbeddersManager::class.java.toString() + ".FOR_FOLDERS_RESOLVE")
    @JvmField
    val FOR_POST_PROCESSING: Key<*> = Key.create<Any?>(MavenEmbeddersManager::class.java.toString() + ".FOR_POST_PROCESSING")
    @JvmField
    val FOR_MODEL_READ: Key<*> = Key.create<Any?>(MavenEmbeddersManager::class.java.toString() + ".FOR_MODEL_READ")

    // will always regardless to 'work offline' setting
    @JvmField
    val FOR_DOWNLOAD: Key<*> = Key.create<Any?>(MavenEmbeddersManager::class.java.toString() + ".FOR_DOWNLOAD")
  }
}
