// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.importing.MavenImportUtil
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.utils.MavenUtil.getJdkForImporter
import org.jetbrains.idea.maven.utils.MavenUtil.isCompatibleWith
import java.rmi.RemoteException

internal class MavenEmbedderWrapperImpl(
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

@ApiStatus.Obsolete
internal class MavenEmbedderWrapperLegacyImpl(
  private val project: Project,
  private val alwaysOnline: Boolean,
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
    var settings = MavenImportUtil.convertSettings(project, MavenProjectsManager.getInstance(project).generalSettings, multiModuleProjectDirectory)
    if (alwaysOnline && settings.isOffline) {
      settings = settings.clone()
      settings.isOffline = false
    }

    val transformer = RemotePathTransformerFactory.createForProject(project)
    var sdkPath = MavenUtil.getSdkPath(ProjectRootManager.getInstance(project).projectSdk)
    if (sdkPath != null) {
      sdkPath = transformer.toRemotePath(sdkPath)
    }
    settings.projectJdk = sdkPath

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