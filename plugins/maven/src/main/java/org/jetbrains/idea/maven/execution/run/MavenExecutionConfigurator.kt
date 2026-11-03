// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.run

import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.EelApi
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.jetbrains.idea.maven.server.MavenDistribution
import java.nio.file.Path

val MAVEN_EXECUTION_CONFIGURATOR: ExtensionPointName<MavenExecutionConfiguratorProvider> = ExtensionPointName(
  "org.jetbrains.idea.maven.runner.executionConfigurator")

/**
 * Provides a mechanism for creating instances of [MavenExecutionConfigurator].
 *
 * An implementation of this interface is responsible for preparing a specific
 * configurator instance that customizes the execution parameters for a Maven build process if applicable.
 * The call runs off the EDT and can suspend, for example to prepare files in [MavenExecutionContext.eelApi].
 */
@ApiStatus.OverrideOnly
interface MavenExecutionConfiguratorProvider {
  @Deprecated("Use suspend createConfigurator(environment, myConfiguration, context) instead")
  fun createConfigurator(environment: ExecutionEnvironment, myConfiguration: MavenRunConfiguration): MavenExecutionConfigurator? = null

  suspend fun createConfigurator(
    environment: ExecutionEnvironment,
    myConfiguration: MavenRunConfiguration,
    context: MavenExecutionContext,
  ): MavenExecutionConfigurator? = createConfigurator(environment, myConfiguration)
}

/**
 * The Maven execution that a [MavenExecutionConfigurator] customizes.
 * Each [Path] in this context is a path of the IDE file system.
 * The process parameters and the environment variables hold the paths of [eelApi].
 *
 * @property eelApi the environment that runs the Maven process.
 * @property distribution the Maven distribution that the IDE resolved for the working directory.
 * @property mavenHome the home of the Maven that the script in the first parameter runs.
 * For bundled Maven, it is the copy in [eelApi]. It is null when the script is the Maven wrapper of the project.
 * @property jdk the JDK that `JAVA_HOME` refers to. The environment variables of the runner settings can override `JAVA_HOME`.
 */
class MavenExecutionContext(
  val eelApi: EelApi,
  val distribution: MavenDistribution,
  val mavenHome: Path?,
  val jdk: Sdk,
)

/**
 * An interface for configuring Maven execution parameters. Implementations of this interface allow for
 * modifications to the environment variables and command-line arguments used during a Maven build execution.
 */
@ApiStatus.OverrideOnly
interface MavenExecutionConfigurator {
  fun configureParameters(
    env: MutableMap<String, String>,
    parametersList: ParametersList,
  )
}
