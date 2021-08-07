// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.coroutine

import com.intellij.execution.configurations.DebuggingRunnerData
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManagerListener
import org.jetbrains.kotlin.idea.debugger.KotlinDebuggerSettings
import org.jetbrains.kotlin.idea.debugger.coroutine.util.logger

interface DebuggerListener : XDebuggerManagerListener {
    fun registerDebuggerConnection(
        configuration: RunConfigurationBase<*>,
        params: JavaParameters?,
        runnerSettings: RunnerSettings?
    ): DebuggerConnection?
}

class CoroutineDebuggerListener(val project: Project) : DebuggerListener {
    val log by logger

    override fun registerDebuggerConnection(
        configuration: RunConfigurationBase<*>,
        params: JavaParameters?,
        runnerSettings: RunnerSettings?
    ): DebuggerConnection? {
        val disableCoroutineAgent = KotlinDebuggerSettings.getInstance().debugDisableCoroutineAgent
        if (!disableCoroutineAgent && runnerSettings is DebuggingRunnerData) {
            return DebuggerConnection(
                project,
                configuration,
                params,
                argumentsShouldBeModified(configuration)
            )
        }
        return null
    }

    private fun RunConfigurationBase<*>.isGradleConfiguration(): Boolean {
        val name = type.id
        return name == "GradleRunConfiguration" || name == "KotlinGradleRunConfiguration"
    }

    private fun RunConfigurationBase<*>.isMavenConfiguration() =
        type.id == "MavenRunConfiguration"

    private fun argumentsShouldBeModified(configuration: RunConfigurationBase<*>) =
        !configuration.isGradleConfiguration() && !configuration.isMavenConfiguration()
}