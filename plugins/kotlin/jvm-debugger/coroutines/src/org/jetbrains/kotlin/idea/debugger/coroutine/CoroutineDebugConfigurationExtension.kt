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
                shouldAttachCoroutineAgent = true // TODO: define that a coroutine agent should be attached properly, see IDEA-368470
            )
        }
    }

    override fun isApplicableFor(configuration: RunConfigurationBase<*>) =
        true
}

private const val KOTLINX_COROUTINES_DEBUG_PROPERTY_NAME = "kotlinx.coroutines.debug"
private const val KOTLINX_COROUTINES_DEBUG_OFF_VALUE = "off"

private fun JavaParameters.isKotlinxCoroutinesDebugDisabled(): Boolean {
    val kotlinxCoroutinesDebugProperty = vmParametersList.properties[KOTLINX_COROUTINES_DEBUG_PROPERTY_NAME]
    return kotlinxCoroutinesDebugProperty == KOTLINX_COROUTINES_DEBUG_OFF_VALUE
}
