// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.util.function.Predicate

interface MavenServerManager : Disposable {
  fun getAllConnectors(): Collection<MavenServerConnector>

  fun restartMavenConnectors(project: Project, wait: Boolean, condition: Predicate<MavenServerConnector>)

  @ApiStatus.ScheduledForRemoval
  @Deprecated("use suspend", ReplaceWith("getConnector"))
  fun getConnectorBlocking(project: Project, workingDirectory: String): MavenServerConnector

  suspend fun getConnector(project: Project, workingDirectory: String): MavenServerConnector

  fun shutdownConnector(connector: MavenServerConnector, wait: Boolean): Boolean

  @TestOnly
  fun closeAllConnectorsAndWait()

  fun getMavenEventListener(): File

  @ApiStatus.ScheduledForRemoval
  @Deprecated("use {@link MavenServerManager#createEmbedder(Project, boolean, String)}",
              ReplaceWith("createEmbedder(project, alwaysOnline, multiModuleProjectDirectory)"))
  fun createEmbedder(project: Project,
                     alwaysOnline: Boolean,
                     ignoredWorkingDirectory: String?,
                     multiModuleProjectDirectory: String): MavenEmbedderWrapper {
    return createEmbedder(project, alwaysOnline, multiModuleProjectDirectory)
  }

  fun createEmbedder(project: Project,
                     alwaysOnline: Boolean,
                     multiModuleProjectDirectory: String): MavenEmbedderWrapper

  @ApiStatus.ScheduledForRemoval
  @Deprecated("use createIndexer()")
  fun createIndexer(project: Project): MavenIndexerWrapper

  fun createIndexer(): MavenIndexerWrapper


  @Deprecated("use {@link MavenGeneralSettings.getMavenHome()} and {@link MavenUtil.getMavenVersion()}",
                  ReplaceWith("MavenGeneralSettings.getMavenHome() or MavenUtil.getMavenVersion()"))
  fun getCurrentMavenVersion(): String? = null

  val isUseMaven2: Boolean
    get() = false

  @ApiStatus.Internal
  interface MavenServerConnectorFactory {
    fun create(project: Project,
               jdk: Sdk,
               vmOptions: String,
               debugPort: Int?,
               mavenDistribution: MavenDistribution,
               multimoduleDirectory: String): MavenServerConnector
  }

  @ApiStatus.Internal
  open class MavenServerConnectorFactoryImpl : MavenServerConnectorFactory {
    override fun create(project: Project,
                        jdk: Sdk,
                        vmOptions: String,
                        debugPort: Int?,
                        mavenDistribution: MavenDistribution,
                        multimoduleDirectory: String): MavenServerConnector {
      return MavenServerConnectorImpl(project, jdk, vmOptions, debugPort, mavenDistribution, multimoduleDirectory)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): MavenServerManager = ApplicationManager.getApplication().getService(MavenServerManager::class.java)
  }
}
