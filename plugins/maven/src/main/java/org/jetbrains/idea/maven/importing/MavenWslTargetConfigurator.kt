// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.execution.target.TargetEnvironmentsManager
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslPath
import com.intellij.execution.wsl.target.WslTargetEnvironmentConfiguration
import com.intellij.execution.wsl.target.WslTargetType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.upgradeBlocking
import org.jetbrains.idea.maven.execution.target.MavenRuntimeTargetConfiguration
import org.jetbrains.idea.maven.project.MavenInSpecificPath
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.LocalMavenDistribution
import org.jetbrains.idea.maven.server.MavenDistribution
import org.jetbrains.idea.maven.server.MavenDistributionsCache
import org.jetbrains.idea.maven.utils.MavenEelUtil.findMavenDistribution
import org.jetbrains.idea.maven.utils.MavenUtil.getJdkForImporter
import java.nio.file.Path

private val MAVEN_HOME_DIR = Key.create<Path>("MAVEN_HOME_DIR")
private val MAVEN_HOME_VERSION = Key.create<String>("MAVEN_WSL_HOME_VERSION")
private val MAVEN_TARGET_PATH = Key.create<String>("MAVEN_TARGET_PATH")
private val WSL_DISTRIBUTION = Key.create<WSLDistribution>("WSL_DISTRIBUTION")
private val JDK_PATH = Key.create<String>("JDK_PATH")

class MavenWslTargetConfigurator : MavenWorkspaceConfigurator {
  override fun beforeModelApplied(context: MavenWorkspaceConfigurator.MutableModelContext) {
    prepareMavenData(context.project, context)
  }

  override fun afterModelApplied(context: MavenWorkspaceConfigurator.AppliedModelContext) {
    if (!SystemInfo.isWindows) return
    configureForProject(context.project, context)
  }

  private fun prepareMavenData(project: Project, dataHolder: UserDataHolder) {
    val wslDistribution = project.basePath?.let { project.tryGetWslDistribution() } ?: return
    dataHolder.putUserData(WSL_DISTRIBUTION, wslDistribution)

    val eel = project.getEelDescriptor().upgradeBlocking()
    val distribution = eel.findMavenDistribution()?.asMavenDistribution() ?: project.getMavenUsedForSync()
    val mavenHome = distribution.mavenHome
    dataHolder.putUserData(MAVEN_HOME_DIR, mavenHome)
    dataHolder.putUserData(MAVEN_TARGET_PATH, mavenHome.asEelPath().toString())
    dataHolder.putUserData(MAVEN_HOME_VERSION, distribution.version)

    val jdkPath = getJdkPath(project)
    dataHolder.putUserData(JDK_PATH, jdkPath)
  }

  private fun getJdkPath(project: Project): String? {
    val jdk = ProjectRootManager.getInstance(project).getProjectSdk() ?: getJdkForImporter(project)
    return jdk.homePath
  }

  private fun MavenInSpecificPath.asMavenDistribution(): MavenDistribution {
    return LocalMavenDistribution(Path.of(mavenHome), title)
  }

  private fun Project.getMavenUsedForSync(): MavenDistribution {
    return MavenDistributionsCache.getInstance(this).getMavenDistribution(basePath)
  }

  private fun configureForProject(project: Project, dataHolder: UserDataHolder) {
    val wslDistribution = dataHolder.getUserData(WSL_DISTRIBUTION)
    if (wslDistribution == null) {
      return
    }
    val configuration = TargetEnvironmentsManager.getInstance(project).targets.resolvedConfigs().find {
      it.typeId == WslTargetType.TYPE_ID
      && (it as? WslTargetEnvironmentConfiguration)?.distribution == wslDistribution
    } as? WslTargetEnvironmentConfiguration
    val javaConfiguration = configuration?.runtimes?.findByType(JavaLanguageRuntimeConfiguration::class.java)

    val mavenConfiguration = configuration?.runtimes?.findByType(MavenRuntimeTargetConfiguration::class.java)
    val targetConfiguration = configuration ?: createWslTarget(project, wslDistribution)
    javaConfiguration ?: createJavaConfiguration(targetConfiguration, project, dataHolder, wslDistribution)
    mavenConfiguration ?: createMavenConfiguration(targetConfiguration, project, dataHolder, wslDistribution)
  }

  private fun createMavenConfiguration(
    configuration: WslTargetEnvironmentConfiguration,
    project: Project,
    dataHolder: UserDataHolder,
    wslDistribution: WSLDistribution,
  ): MavenRuntimeTargetConfiguration? {
    val mavenConfig = MavenRuntimeTargetConfiguration()
    val targetMavenPath = dataHolder.getUserData(MAVEN_TARGET_PATH)

    if (targetMavenPath == null) {
      MavenProjectsManager.getInstance(project).syncConsole.addWarning(MavenProjectBundle.message("wsl.misconfigured.title"),
                                                                       MavenProjectBundle.message("wsl.does.not.have.configured.maven",
                                                                                                  wslDistribution.presentableName))
      return null
    }

    val mavenVersion = dataHolder.getUserData(MAVEN_HOME_VERSION)
    mavenConfig.homePath = targetMavenPath
    mavenConfig.versionString = mavenVersion ?: ""
    configuration.addLanguageRuntime(mavenConfig)
    return mavenConfig
  }

  private fun createJavaConfiguration(
    configuration: WslTargetEnvironmentConfiguration,
    project: Project,
    dataHolder: UserDataHolder,
    wslDistribution: WSLDistribution,
  ): JavaLanguageRuntimeConfiguration? {
    val javaConfig = JavaLanguageRuntimeConfiguration()
    val jdkPath = dataHolder.getUserData(JDK_PATH)

    if (jdkPath == null) {
      MavenProjectsManager.getInstance(project).syncConsole.addWarning(MavenProjectBundle.message("wsl.misconfigured.title"),
                                                                       MavenProjectBundle.message("wsl.does.not.have.configured.jdk",
                                                                                                  wslDistribution.presentableName))
      return null
    }
    javaConfig.homePath = jdkPath
    configuration.addLanguageRuntime(javaConfig)

    return javaConfig
  }

  private fun createWslTarget(project: Project, wslDistribution: WSLDistribution): WslTargetEnvironmentConfiguration {
    val configuration = WslTargetEnvironmentConfiguration(wslDistribution)
    configuration.displayName = "WSL" //NON-NLS
    TargetEnvironmentsManager.getInstance(project).addTarget(configuration)
    return configuration
  }
}

private fun Project.tryGetWslDistribution(): WSLDistribution? {
  return basePath?.let { WslPath.getDistributionByWindowsUncPath(it) }
}