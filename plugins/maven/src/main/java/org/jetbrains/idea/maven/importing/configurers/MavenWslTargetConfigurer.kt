// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing.configurers

import com.intellij.execution.target.TargetEnvironmentsManager
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.target.WslTargetEnvironmentConfiguration
import com.intellij.execution.wsl.target.WslTargetType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.idea.maven.execution.target.MavenRuntimeTargetConfiguration
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.utils.MavenWslUtil
import org.jetbrains.idea.maven.utils.MavenWslUtil.resolveMavenHomeDirectory


class MavenWslTargetConfigurer : MavenModuleConfigurer() {
  override fun configure(mavenProject: MavenProject,
                         project: Project,
                         module: Module) {
    val wslDistribution = ReadAction.compute<WSLDistribution, Throwable> {
      project.basePath?.let { MavenWslUtil.tryGetWslDistribution(project) }
    }
    if (wslDistribution == null) {
      return;
    }
    val configuration = TargetEnvironmentsManager.getInstance(project).targets.resolvedConfigs().find {
      it.typeId == WslTargetType.TYPE_ID
      && (it as? WslTargetEnvironmentConfiguration)?.distribution == wslDistribution
    } as? WslTargetEnvironmentConfiguration
    val javaConfiguration = configuration?.runtimes?.findByType(JavaLanguageRuntimeConfiguration::class.java)

    val mavenConfiguration = configuration?.runtimes?.findByType(MavenRuntimeTargetConfiguration::class.java)
    val targetConfiguration = configuration ?: WriteAction.computeAndWait<WslTargetEnvironmentConfiguration, Throwable> {
      createWslTarget(project, wslDistribution)
    }
    javaConfiguration ?: createJavaConfiguration(targetConfiguration, project, wslDistribution)
    mavenConfiguration ?: createMavenConfiguration(targetConfiguration, project, wslDistribution)

  }

  private fun createMavenConfiguration(configuration: WslTargetEnvironmentConfiguration,
                                       project: Project,
                                       wslDistribution: WSLDistribution): MavenRuntimeTargetConfiguration? {
    val mavenConfig = MavenRuntimeTargetConfiguration()
    val mavenPath = wslDistribution.resolveMavenHomeDirectory(null)
    val targetMavenPath = mavenPath?.let { wslDistribution.getWslPath(it.path) }

    if (targetMavenPath == null) {
      MavenProjectsManager.getInstance(project).syncConsole.addWarning(MavenProjectBundle.message("wsl.misconfigured.title"),
                                                                       MavenProjectBundle.message("wsl.does.not.have.configured.maven",
                                                                                                  wslDistribution.presentableName));
      return null;
    }

    val mavenVersion = MavenUtil.getMavenVersion(mavenPath);
    mavenConfig.homePath = targetMavenPath
    mavenConfig.versionString = mavenVersion ?: ""
    WriteAction.runAndWait<Throwable> {
      configuration.addLanguageRuntime(mavenConfig)
    }
    return mavenConfig
  }

  private fun createJavaConfiguration(configuration: WslTargetEnvironmentConfiguration,
                                      project: Project,
                                      wslDistribution: WSLDistribution): JavaLanguageRuntimeConfiguration? {
    val javaConfig = JavaLanguageRuntimeConfiguration()
    val jdkPath = getJdkPath(project, wslDistribution)
    if (jdkPath == null) {
      MavenProjectsManager.getInstance(project).syncConsole.addWarning(MavenProjectBundle.message("wsl.misconfigured.title"),
                                                                       MavenProjectBundle.message("wsl.does.not.have.configured.jdk",
                                                                                                  wslDistribution.presentableName));
      return null;
    }
    javaConfig.homePath = jdkPath;
    WriteAction.runAndWait<Throwable> {
      configuration.addLanguageRuntime(javaConfig)
    }

    return javaConfig
  }

  private fun getJdkPath(project: Project, wslDistribution: WSLDistribution): String? {
    val projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    if (projectSdk != null && MavenWslUtil.tryGetWslDistributionForPath(projectSdk.homePath) == wslDistribution) {
      projectSdk.homePath?.let { wslDistribution.getWslPath(it) }?.let { return@getJdkPath it }
    }
    return MavenWslUtil.getJdkPath(wslDistribution)
  }

  private fun createWslTarget(project: Project, wslDistribution: WSLDistribution): WslTargetEnvironmentConfiguration {
    val configuration = WslTargetEnvironmentConfiguration(wslDistribution)
    configuration.displayName = "WSL"
    TargetEnvironmentsManager.getInstance(project).addTarget(configuration)
    return configuration
  }

}