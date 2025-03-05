// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.importing.MavenImportUtil.convertSettings
import org.jetbrains.idea.maven.server.*
import org.jetbrains.idea.maven.utils.MavenUtil
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

private class MavenEmbedderWrappersImpl(private val myProject: Project) : MavenEmbedderWrappers {
  private val mutex = Mutex()
  private val myEmbedders = ConcurrentHashMap<Path, MavenEmbedderWrapper>()
  private val jdk = getJdkForImporter(myProject)
  private val generalSettings = MavenProjectsManager.getInstance(myProject).generalSettings
  private val cache = MavenDistributionsCache.getInstance(myProject)

  override suspend fun getAlwaysOnlineEmbedder(baseDir: String): MavenEmbedderWrapper = getEmbedder(Path.of(baseDir), true)

  override suspend fun getEmbedder(baseDir: Path): MavenEmbedderWrapper = getEmbedder(baseDir, false)

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
      val multiModuleProjectDirectory = cache.getMultimoduleDirectory(embedderDir)
      val connector = MavenServerManager.getInstance().getConnector(myProject, multiModuleProjectDirectory, jdk)

      val mavenDistribution = MavenDistributionsCache.getInstance(myProject).getMavenDistribution(multiModuleProjectDirectory)
      var settings = convertSettings(myProject, generalSettings, mavenDistribution)
      if (alwaysOnline && settings.isOffline) {
        settings = settings.clone()
        settings.isOffline = false
      }

      val transformer = RemotePathTransformerFactory.createForProject(myProject)
      var sdkPath = MavenUtil.getSdkPath(ProjectRootManager.getInstance(myProject).projectSdk)
      if (sdkPath != null) {
        sdkPath = transformer.toRemotePath(sdkPath)
      }
      settings.projectJdk = sdkPath

      val forceResolveDependenciesSequentially = Registry.Companion.`is`("maven.server.force.resolve.dependencies.sequentially")
      val useCustomDependenciesResolver = Registry.Companion.`is`("maven.server.use.custom.dependencies.resolver")

      val embedder = connector.createEmbedder(MavenEmbedderSettings(
        settings,
        transformer.toRemotePath(multiModuleProjectDirectory),
        forceResolveDependenciesSequentially,
        useCustomDependenciesResolver
      ))

      val newEmbedder = MavenEmbedderWrapperImpl(myProject, embedder)
      myEmbedders[baseDir] = newEmbedder
      return newEmbedder
    }
  }

  override fun close() {
    myEmbedders.values.forEach { it.release() }
    myEmbedders.clear()
  }
}