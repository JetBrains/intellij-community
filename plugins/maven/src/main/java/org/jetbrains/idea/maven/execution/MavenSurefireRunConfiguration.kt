// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.debugger.impl.RemoteConnectionBuilder
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.configurations.RemoteConnectionCreator
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.util.JavaParametersUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry

class MavenSurefireConfigurationFactory(
  private val configurationName: String,
  private val alternativeJrePath: String?,
) : ConfigurationFactory(MavenRunConfigurationType.getInstance()) {

  override fun getId(): String = "MavenSurefire"

  override fun createTemplateConfiguration(project: Project): RunConfiguration =
    MavenSurefireRunConfiguration(project, this, configurationName, alternativeJrePath)

  override fun createConfiguration(name: String?, template: RunConfiguration): RunConfiguration =
    MavenSurefireRunConfiguration(template.project, this, configurationName, alternativeJrePath)
}

class MavenSurefireRunConfiguration(
  project: Project?,
  configurationFactory: ConfigurationFactory?,
  name: String,
  private val alternativeJrePath: String?,
) : MavenRunConfiguration(project, configurationFactory, name), SurefireRunConfiguration {

  override fun createRemoteConnectionCreator(javaParameters: JavaParameters): RemoteConnectionCreator {
    return object : RemoteConnectionCreator {
      override fun createRemoteConnection(environment: ExecutionEnvironment): RemoteConnection? {
        try {
          val parameters = JavaParameters()
          parameters.jdk = JavaParametersUtil.createProjectJdk(project, alternativeJrePath)
          // RemoteConnectionBuilder populates parameters.vmParametersList with the JDWP agent string.
          val connection = RemoteConnectionBuilder(false, DebuggerSettings.getInstance().transport, "")
            .asyncAgent(Registry.`is`("maven.use.scripts.debug.agent"))
            .project(environment.project)
            .matchWithExecutionTarget()
            .create(parameters)

          // patchVmParameters flips suspend=n,server=y -> suspend=y,server=y, so the test JVM waits for the debugger.
          val jdwpArgs = MavenExecutionEnvironmentProviderUtil.patchVmParameters(parameters.vmParametersList)
            .joinToString(" ")
          if (jdwpArgs.isNotEmpty()) {
            javaParameters.programParametersList.add("-Dmaven.surefire.debug=$jdwpArgs")
          }
          return connection
        }
        catch (e: ExecutionException) {
          throw RuntimeException("Cannot create debug connection", e)
        }
      }

      override fun isPollConnection(): Boolean = true
    }
  }
}

/** Identifies a [MavenRunConfiguration] that runs tests through Maven Surefire. */
interface SurefireRunConfiguration
