// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.idea.maven.config.MavenConfigSettings
import org.jetbrains.idea.maven.project.MavenGeneralSettings
import org.jetbrains.idea.maven.project.MavenInSpecificPath
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.utils.MavenEelUtil
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.utils.MavenUtil.getJdkForImporter
import org.jetbrains.idea.maven.utils.MavenUtil.isCompatibleWith
import java.nio.file.Path
import java.rmi.RemoteException
import kotlin.io.path.absolutePathString

internal class MavenEmbedderWrapperImpl(
  private val project: Project,
  private val alwaysOnline: Boolean,
  private val multiModuleProjectDirectory: String,
  private val mavenServerManager: MavenServerManager,
) : MavenEmbedderWrapper(project) {
  private var myConnector: MavenServerConnector? = null

  val createMutex = Mutex()

  @Throws(RemoteException::class)
  override suspend fun create(): MavenServerEmbedder {
    return createMutex.withLock { doCreate() }
  }

  @Throws(RemoteException::class)
  private suspend fun doCreate(): MavenServerEmbedder {
    var settings = convertSettings(project, MavenProjectsManager.getInstance(project).generalSettings, multiModuleProjectDirectory)
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

    myConnector = mavenServerManager.getConnector(project, multiModuleProjectDirectory)
    return myConnector!!.createEmbedder(MavenEmbedderSettings(
      settings,
      transformer.toRemotePath(multiModuleProjectDirectory),
      forceResolveDependenciesSequentially,
      useCustomDependenciesResolver
    ))
  }

  private fun convertSettings(
    project: Project,
    settingsOptional: MavenGeneralSettings?,
    multiModuleProjectDirectory: String,
  ): MavenServerSettings {
    val settings = settingsOptional ?: MavenWorkspaceSettingsComponent.getInstance(project).settings.generalSettings
    val transformer = RemotePathTransformerFactory.createForProject(project)
    val result = MavenServerSettings()
    result.loggingLevel = settings!!.outputLevel.level
    result.isOffline = settings.isWorkOffline
    result.isUpdateSnapshots = settings.isAlwaysUpdateSnapshots
    val mavenDistribution = MavenDistributionsCache.getInstance(project).getMavenDistribution(multiModuleProjectDirectory)

    val remotePath = transformer.toRemotePath(mavenDistribution.mavenHome.toString())
    result.mavenHomePath = remotePath

    val userSettings = MavenEelUtil.getUserSettings(project, settings.userSettingsFile, settings.mavenConfig)
    val userSettingsPath = userSettings.toAbsolutePath().toString()
    result.userSettingsPath = transformer.toRemotePath(userSettingsPath)

    val localRepository = MavenEelUtil.getLocalRepo(project,
                                                    settings.localRepository,
                                                    MavenInSpecificPath(mavenDistribution.mavenHome),
                                                    settings.userSettingsFile,
                                                    settings.mavenConfig)
      .toAbsolutePath().toString()

    result.localRepositoryPath = transformer.toRemotePath(localRepository)
    val file = getGlobalConfigFromMavenConfig(settings) ?: MavenUtil.resolveGlobalSettingsFile(mavenDistribution.mavenHome)
    result.globalSettingsPath = transformer.toRemotePath(file.absolutePathString())
    return result
  }

  private fun getGlobalConfigFromMavenConfig(settings: MavenGeneralSettings): Path? {
    val mavenConfig = settings.mavenConfig ?: return null
    val filePath = mavenConfig.getFilePath(MavenConfigSettings.ALTERNATE_GLOBAL_SETTINGS) ?: return null
    return Path.of(filePath)
  }

  override fun isCompatibleWith(project: Project, multiModuleDirectory: String): Boolean {
    val jdk = getJdkForImporter(project)
    return myConnector?.isCompatibleWith(project, jdk, multiModuleDirectory) ?: false
  }

  @Synchronized
  override fun cleanup() {
    super.cleanup()
    if (myConnector != null) {
      mavenServerManager.shutdownConnector(myConnector!!, false)
    }
  }
}