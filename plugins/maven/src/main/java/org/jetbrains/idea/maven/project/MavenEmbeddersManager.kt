// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.importing.MavenImportUtil
import org.jetbrains.idea.maven.importing.MavenImportUtil.guessExistingEmbedderDir
import org.jetbrains.idea.maven.server.*
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil.getBaseDir
import org.jetbrains.idea.maven.utils.MavenUtil.getJdkForImporter
import org.jetbrains.idea.maven.utils.MavenUtil.isCompatibleWith
import java.rmi.RemoteException

@ApiStatus.Obsolete
class MavenEmbeddersManager(private val project: Project) {
  private val myPool: MutableMap<Pair<Key<*>, String>, MavenEmbedderWrapperLegacyImpl> = ContainerUtil.createSoftValueMap<Pair<Key<*>, String>, MavenEmbedderWrapperLegacyImpl>()
  private val myEmbeddersInUse: MutableSet<MavenEmbedderWrapperLegacyImpl> = HashSet<MavenEmbedderWrapperLegacyImpl>()

  @Synchronized
  fun reset() {
    for (each in myPool.keys) {
      val embedder = myPool[each]
      if (embedder == null) continue  // collected

      if (myEmbeddersInUse.contains(embedder)) continue
      embedder.release()
    }
    myPool.clear()
    myEmbeddersInUse.clear()
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

    if (result != null && true && !result.isCompatibleWith(project, multiModuleProjectDirectory)) {
      myPool.remove(key)
      myEmbeddersInUse.remove(result)
      result = null
    }

    if (result == null) {
      result = createEmbedder(embedderDir)
      myPool[key] = result
    }

    if (myEmbeddersInUse.contains(result)) {
      MavenLog.LOG.warn("embedder $key is already used")
      return createEmbedder(embedderDir)
    }

    myEmbeddersInUse.add(result)
    return result
  }

  private fun createEmbedder(multiModuleProjectDirectory: String): MavenEmbedderWrapperLegacyImpl {
    val connector = MavenServerManager.getInstance().getConnectorBlocking(project, multiModuleProjectDirectory)
    return MavenEmbedderWrapperLegacyImpl(project, multiModuleProjectDirectory, connector)
  }

  @Synchronized
  fun release(embedder: MavenEmbedderWrapper) {
    if (!myEmbeddersInUse.contains(embedder)) {
      embedder.release()
      return
    }

    myEmbeddersInUse.remove(embedder)
  }

  companion object {
    // used in third-party plugins
    @JvmField
    val FOR_DEPENDENCIES_RESOLVE: Key<*> = Key.create<Any?>(MavenEmbeddersManager::class.java.toString() + ".FOR_DEPENDENCIES_RESOLVE")
  }
}

@ApiStatus.Obsolete
private class MavenEmbedderWrapperLegacyImpl(
  private val project: Project,
  private val multiModuleProjectDirectory: String,
  private val myConnector: MavenServerConnector
) : MavenEmbedderWrapper(project) {

  val createMutex = Mutex()

  @Throws(RemoteException::class)
  override suspend fun create(): MavenServerEmbedder {
    return createMutex.withLock { doCreate() }
  }

  @Throws(RemoteException::class)
  private suspend fun doCreate(): MavenServerEmbedder {
    val mavenDistribution = MavenDistributionsCache.getInstance(project).getMavenDistribution(multiModuleProjectDirectory)
    val settings = MavenImportUtil.convertSettings(project, MavenProjectsManager.getInstance(project).generalSettings, mavenDistribution)
    val transformer = RemotePathTransformerFactory.createForProject(project)
    val forceResolveDependenciesSequentially = Registry.Companion.`is`("maven.server.force.resolve.dependencies.sequentially")
    val useCustomDependenciesResolver = Registry.Companion.`is`("maven.server.use.custom.dependencies.resolver")

    return myConnector.createEmbedder(MavenEmbedderSettings(
      settings,
      transformer.toRemotePath(multiModuleProjectDirectory),
      forceResolveDependenciesSequentially,
      useCustomDependenciesResolver
    ))
  }

  fun isCompatibleWith(project: Project, multiModuleDirectory: String): Boolean {
    val jdk = getJdkForImporter(project)
    return myConnector.isCompatibleWith(project, jdk, multiModuleDirectory)
  }

  @Synchronized
  override fun cleanup() {
    MavenLog.LOG.debug("[wrapper] cleaning up $this")
    super.cleanup()
  }
}