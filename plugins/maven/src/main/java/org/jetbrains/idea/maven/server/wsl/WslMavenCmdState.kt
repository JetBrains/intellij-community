// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server.wsl

import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.rmi.RemoteServer
import com.intellij.execution.target.TargetEnvironmentAwareRunProfileState
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.target.WslTargetEnvironmentConfiguration
import com.intellij.execution.wsl.target.WslTargetEnvironmentFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JdkCommandLineSetup
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.MavenDistribution
import org.jetbrains.idea.maven.server.MavenServerCMDState
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.utils.MavenWslUtl.getPropertiesFromMavenOpts

class WslMavenCmdState(private val myWslDistribution: WSLDistribution,
                       jdk: Sdk,
                       vmOptions: String?,
                       mavenDistribution: MavenDistribution?,
                       project: Project?,
                       debugPort: Int?
) : MavenServerCMDState(jdk, vmOptions, mavenDistribution, project, debugPort) {

  override fun getMavenOpts(): Map<String, String> {
    return getPropertiesFromMavenOpts(myWslDistribution)
  }

  override fun getWorkingDirectory(): String {
    return myWslDistribution.userHome ?: myWslDistribution.getWslPath(super.getWorkingDirectory()) ?: "/";
  }

  override fun createJavaParameters(): SimpleJavaParameters {
    val parameters = super.createJavaParameters();
    val wslParams = toWslParameters(parameters)
    wslParams.vmParametersList.add(RemoteServer.SERVER_HOSTNAME, myWslDistribution.hostIp)
    wslParams.vmParametersList.add("idea.maven.knownPort", "true")
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
    for (item in parameters.classPath.pathList) {
      wslParams.classPath.add(myWslDistribution.getWslPath(item))
    }
    wslParams.setWorkingDirectory(parameters.workingDirectory)
    return wslParams
  }

  override fun startProcess(): ProcessHandler {
    val wslConfig = WslTargetEnvironmentConfiguration(myWslDistribution)
    val myEnvFactory = WslTargetEnvironmentFactory(wslConfig)

    val params = createJavaParameters()
    val request = myEnvFactory.createRequest();
    myEnvFactory.targetConfiguration.addLanguageRuntime(JavaLanguageRuntimeConfiguration())
    val setup = JdkCommandLineSetup(request, myEnvFactory.targetConfiguration)
    setup.setupCommandLine(params)
    setup.setupJavaExePath(params)

    val builder = params
      .toCommandLine(myEnvFactory.createRequest(), wslConfig)
    builder.setWorkingDirectory(workingDirectory)

    val commandLine = builder.build()

    val wslEnvironment = myEnvFactory.prepareRemoteEnvironment(request,
                                                               TargetEnvironmentAwareRunProfileState.TargetProgressIndicator.EMPTY)
    setup.provideEnvironment(wslEnvironment, TargetEnvironmentAwareRunProfileState.TargetProgressIndicator.EMPTY)

    val manager = MavenProjectsManager.getInstance(myProject)
    val process = wslEnvironment.createProcess(commandLine, MavenProgressIndicator(myProject, manager::getSyncConsole).indicator)
    return MavenWslProcessHandler(process, commandLine.getCommandPresentation(wslEnvironment), myWslDistribution)
  }
}