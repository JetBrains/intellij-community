// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.coroutine

import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configurations.DebuggingRunnerData
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import org.jetbrains.kotlin.idea.debugger.KotlinDebuggerSettings

private class CoroutineDebugConfigurationExtension : RunConfigurationExtension() {
    override fun <T : RunConfigurationBase<*>> updateJavaParameters(
        configuration: T,
        params: JavaParameters,
        runnerSettings: RunnerSettings?
    ) {
        val coroutineDebugIsDisabledInSettings = KotlinDebuggerSettings.getInstance().debugDisableCoroutineAgent
        val coroutineDebugIsDisabledInParameters = params.isKotlinxCoroutinesDebugDisabled()
        if (!coroutineDebugIsDisabledInSettings &&
            !coroutineDebugIsDisabledInParameters &&
            runnerSettings is DebuggingRunnerData
        )
        {
            DebuggerConnection(
                configuration.project,
                configuration,
                params,
                isExternalSystemRunConfiguration(configuration),
            )
        }
    }

    override fun isApplicableFor(configuration: RunConfigurationBase<*>) =
        true
}

private const val KOTLINX_COROUTINES_DEBUG_PROPERTY_NAME = "kotlinx.coroutines.debug"
private const val KOTLINX_COROUTINES_DEBUG_OFF_VALUE = "off"
private const val GRADLE_RUN_CONFIGURATION = "GradleRunConfiguration"
private const val KOTLIN_GRADLE_RUN_CONFIGURATION = "KotlinGradleRunConfiguration"
private const val MAVEN_RUN_CONFIGURATION = "MavenRunConfiguration"

private fun JavaParameters.isKotlinxCoroutinesDebugDisabled(): Boolean {
    val kotlinxCoroutinesDebugProperty = vmParametersList.properties[KOTLINX_COROUTINES_DEBUG_PROPERTY_NAME]
    return kotlinxCoroutinesDebugProperty == KOTLINX_COROUTINES_DEBUG_OFF_VALUE
}

// Check if the given configuration is an external system run configuration,
// as they do not depend on the IDE/JPS model, attach of the debug agent should be handled with external extensions.
// See `KotlinCoroutineJvmDebugInit.gradle` for GradleRunConfiguration.
private fun isExternalSystemRunConfiguration(configuration: RunConfigurationBase<*>): Boolean =
    !configuration.isGradleConfiguration() && !configuration.isMavenConfiguration()

private fun RunConfigurationBase<*>.isGradleConfiguration(): Boolean {
    val name = type.id
    return name == GRADLE_RUN_CONFIGURATION || name == KOTLIN_GRADLE_RUN_CONFIGURATION
}

private fun RunConfigurationBase<*>.isMavenConfiguration(): Boolean =
    type.id == MAVEN_RUN_CONFIGURATION
