// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.importing.MavenImportUtil.convertSettings
import org.jetbrains.idea.maven.server.*
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil.getJdkForImporter
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
interface MavenEmbedderWrappers : AutoCloseable {
  suspend fun getEmbedder(baseDir: String): MavenEmbedderWrapper = getEmbedder(Path.of(baseDir))
  suspend fun getAlwaysOnlineEmbedder(baseDir: String): MavenEmbedderWrapper
  suspend fun getEmbedder(baseDir: Path): MavenEmbedderWrapper
}

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class MavenEmbedderWrappersManager(private val project: Project) {
  fun createMavenEmbedderWrappers() : MavenEmbedderWrappers {
    return MavenEmbedderWrappersImpl(project)
  }
}

private class MavenEmbedderWrappersImpl(private val project: Project) : MavenEmbedderWrappers {
  private val mutex = Mutex()
  private val embedders = ConcurrentHashMap<Path, MavenEmbedderWrapper>()
  private val jdk = getJdkForImporter(project)
  private val generalSettings = MavenProjectsManager.getInstance(project).generalSettings
  private val cache = MavenDistributionsCache.getInstance(project)

  override suspend fun getAlwaysOnlineEmbedder(baseDir: String): MavenEmbedderWrapper = getEmbedder(Path.of(baseDir), true)

  override suspend fun getEmbedder(baseDir: Path): MavenEmbedderWrapper = getEmbedder(baseDir, false)

  private suspend fun getEmbedder(baseDir: Path, alwaysOnline: Boolean): MavenEmbedderWrapper {
    val embedderDir = baseDir.toString()
    val existing = embedders[baseDir]
    if (null != existing) {
      return existing
    }
    mutex.withLock {
      val existing = embedders[baseDir]
      if (null != existing) {
        return existing
      }
      val multiModuleProjectDirectory = cache.getMultimoduleDirectory(embedderDir)
      val connector = MavenServerManager.getInstance().getConnector(project, multiModuleProjectDirectory, jdk)

      val mavenDistribution = MavenDistributionsCache.getInstance(project).getMavenDistribution(multiModuleProjectDirectory)
      var settings = convertSettings(project, generalSettings, mavenDistribution)
      if (alwaysOnline && settings.isOffline) {
        settings = settings.clone()
        settings.isOffline = false
      }
      val transformer = RemotePathTransformerFactory.createForProject(project)
      val forceResolveDependenciesSequentially = Registry.Companion.`is`("maven.server.force.resolve.dependencies.sequentially")
      val useCustomDependenciesResolver = Registry.Companion.`is`("maven.server.use.custom.dependencies.resolver")

      val embedder = connector.createEmbedder(MavenEmbedderSettings(
        settings,
        transformer.toRemotePath(embedderDir),
        forceResolveDependenciesSequentially,
        useCustomDependenciesResolver
      ))

      val newEmbedder = MavenEmbedderWrapperImpl(project, embedder)
      embedders[baseDir] = newEmbedder
      return newEmbedder
    }
  }

  override fun close() {
    embedders.values.forEach { it.release() }
    embedders.clear()
  }
}

private class MavenEmbedderWrapperImpl(
  project: Project,
  private val myEmbedder: MavenServerEmbedder
) : MavenEmbedderWrapper(project) {

  override suspend fun create(): MavenServerEmbedder {
    return myEmbedder
  }

  @Synchronized
  override fun cleanup() {
    MavenLog.LOG.debug("[wrapper] cleaning up $this")
    super.cleanup()
  }
}