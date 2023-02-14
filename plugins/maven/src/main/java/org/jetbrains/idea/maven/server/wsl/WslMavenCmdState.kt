// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server.wsl

import com.intellij.build.events.MessageEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.rmi.RemoteServer
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.target.WslTargetEnvironmentConfiguration
import com.intellij.execution.wsl.target.WslTargetEnvironmentRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.pom.Navigatable
import org.jetbrains.idea.maven.buildtool.quickfix.OpenMavenImportingSettingsQuickFix
import org.jetbrains.idea.maven.execution.SyncBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.MavenServerCMDState
import org.jetbrains.idea.maven.server.WslMavenDistribution
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import org.jetbrains.idea.maven.utils.MavenWslUtil
import org.jetbrains.idea.maven.utils.MavenWslUtil.getPropertiesFromMavenOpts

internal class WslMavenCmdState(private val myWslDistribution: WSLDistribution,
                       jdk: Sdk,
                       vmOptions: String?,
                       mavenDistribution: WslMavenDistribution,
                       debugPort: Int?,
                       val myProject: Project,
                       val remoteHost: String
) : MavenServerCMDState(jdk, vmOptions, mavenDistribution, debugPort) {

  override fun getMavenOpts(): Map<String, String> {
    return getPropertiesFromMavenOpts(myWslDistribution)
  }

  override fun getWorkingDirectory(): String {
    return myWslDistribution.userHome?: "/"
  }

  override fun createJavaParameters(): SimpleJavaParameters {
    val parameters = super.createJavaParameters()
    val wslParams = toWslParameters(parameters)
    wslParams.vmParametersList.add("-D${RemoteServer.SERVER_HOSTNAME}=${remoteHost}")
    wslParams.vmParametersList.add("-Didea.maven.wsl=true")
    return wslParams
  }

  private fun toWslParameters(parameters: SimpleJavaParameters): SimpleJavaParameters {
    val wslParams = SimpleJavaParameters()
    wslParams.mainClass = parameters.mainClass
    for (item in parameters.vmParametersList.parameters) {
      wslParams.vmParametersList.add(item)
    }
    for (item in parameters.programParametersList.parameters) {
      wslParams.programParametersList.add(item)
    }
    wslParams.charset = parameters.charset
    wslParams.vmParametersList.add("-classpath")
    wslParams.vmParametersList.add(parameters.classPath.pathList
                                     .mapNotNull(myWslDistribution::getWslPath).joinToString(":"))
    return wslParams
  }

  override fun startProcess(): ProcessHandler {
    val wslConfig = WslTargetEnvironmentConfiguration(myWslDistribution)
    val request = WslTargetEnvironmentRequest(wslConfig)

    val wslParams = createJavaParameters()
    val languageRuntime = JavaLanguageRuntimeConfiguration()

    var jdkHomePath = myJdk.homePath
    val projectJdkHomePath = ProjectRootManager.getInstance(myProject).projectSdk?.homePath
    if (MavenWslUtil.tryGetWslDistributionForPath(jdkHomePath) != myWslDistribution && MavenWslUtil.tryGetWslDistributionForPath(
        projectJdkHomePath) != myWslDistribution) {
      MavenProjectsManager.getInstance(myProject).syncConsole.addBuildIssue(BuildIssueWslJdk(), MessageEvent.Kind.WARNING)
    }
    else if (MavenWslUtil.tryGetWslDistributionForPath(jdkHomePath) != myWslDistribution && MavenWslUtil.tryGetWslDistributionForPath(
        projectJdkHomePath) == myWslDistribution) {
      jdkHomePath = projectJdkHomePath
      MavenProjectsManager.getInstance(myProject).syncConsole.addBuildIssue(object : BuildIssue {
        override val title: String = SyncBundle.message("maven.sync.wsl.jdk.set.to.project")
        override val description: String = SyncBundle.message(
          "maven.sync.wsl.jdk.set.to.project") + "\n<a href=\"${OpenMavenImportingSettingsQuickFix.ID}\">" + SyncBundle.message(
          "maven.sync.wsl.jdk.fix") + "</a>"
        override val quickFixes: List<BuildIssueQuickFix> = listOf(OpenMavenImportingSettingsQuickFix())
        override fun getNavigatable(project: Project): Navigatable? = null
      }, MessageEvent.Kind.INFO)
    }

    val wslPath = jdkHomePath?.let(myWslDistribution::getWslPath)
    if (wslPath == null) {
      MavenProjectsManager.getInstance(myProject).syncConsole.addBuildIssue(object : BuildIssue {
        override val title: String = SyncBundle.message("maven.sync.wsl.jdk.revert.usr")
        override val description: String = SyncBundle.message(
          "maven.sync.wsl.jdk") + "\n<a href=\"${OpenMavenImportingSettingsQuickFix.ID}\">" + SyncBundle.message(
          "maven.sync.wsl.jdk.fix") + "</a>"
        override val quickFixes: List<BuildIssueQuickFix> = listOf(OpenMavenImportingSettingsQuickFix())
        override fun getNavigatable(project: Project): Navigatable? = null
      }, MessageEvent.Kind.WARNING)
    }

    languageRuntime.homePath = wslPath ?: "/usr"
    request.configuration.addLanguageRuntime(languageRuntime)

    val builder = wslParams.toCommandLine(request)
    builder.setWorkingDirectory(workingDirectory)

    val wslEnvironment = request.prepareEnvironment(TargetProgressIndicator.EMPTY)

    val manager = MavenProjectsManager.getInstance(myProject)
    val commandLine = builder.build()
    val commandPresentation = commandLine.getCommandPresentation(wslEnvironment)
    MavenLog.LOG.info("Staring maven server on WSL as $commandPresentation")
    val process = wslEnvironment.createProcess(commandLine, MavenProgressIndicator(myProject, manager::getSyncConsole).indicator)
    return MavenWslProcessHandler(process, commandPresentation, myWslDistribution)
  }
}

class BuildIssueWslJdk : BuildIssue {
  override val title: String = SyncBundle.message("maven.sync.wsl.jdk")
  override val description: String = SyncBundle.message(
    "maven.sync.wsl.jdk") + "\n<a href=\"${OpenMavenImportingSettingsQuickFix.ID}\">" + SyncBundle.message(
    "maven.sync.wsl.jdk.fix") + "</a>"
  override val quickFixes: List<BuildIssueQuickFix> = listOf(OpenMavenImportingSettingsQuickFix())
  override fun getNavigatable(project: Project): Navigatable? = null
}