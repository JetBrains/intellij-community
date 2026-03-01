// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.run

import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.idea.maven.execution.MavenRunConfiguration

val MAVEN_EXECUTION_CONFIGURATOR: ExtensionPointName<MavenExecutionConfiguratorProvider> = ExtensionPointName(
  "org.jetbrains.idea.maven.runner.executionConfigurator")

/**
 * Provides a mechanism for creating instances of [MavenExecutionConfigurator].
 *
 * An implementation of this interface is responsible for preparing a specific
 * configurator instance that customizes the execution parameters for a Maven build process if applicable
 *

 */
interface MavenExecutionConfiguratorProvider {
  fun createConfigurator(environment: ExecutionEnvironment, myConfiguration: MavenRunConfiguration): MavenExecutionConfigurator?
}

/**
 * An interface for configuring Maven execution parameters. Implementations of this interface allow for
 * modifications to the environment variables and command-line arguments used during a Maven build execution.
 */
interface MavenExecutionConfigurator {
  fun configureParameters(
    env: MutableMap<String, String>,
    parametersList: ParametersList,
  )
}
