// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.engine.AsyncStacksUtils
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.GenericDebuggerRunnerSettings
import com.intellij.debugger.impl.RemoteConnectionBuilder
import com.intellij.execution.configurations.JavaCommandLineState
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetedCommandLineBuilder

/** Attaches to a Java VM */
internal class JvmAttacher : VmAttacher {

    override fun attachVirtualMachine(
        testCase: KotlinDescriptorTestCase,
        javaParameters: JavaParameters,
        environment: ExecutionEnvironment
    ): DebuggerSession {
        val debuggerRunnerSettings = (environment.runnerSettings as GenericDebuggerRunnerSettings)
        val javaCommandLineState: JavaCommandLineState = object : JavaCommandLineState(environment) {
            override fun createJavaParameters() = javaParameters

            override fun createTargetedCommandLine(request: TargetEnvironmentRequest): TargetedCommandLineBuilder {
                return getJavaParameters().toCommandLine(request)
            }
        }

        val debugParameters =
            RemoteConnectionBuilder(
                debuggerRunnerSettings.LOCAL,
                debuggerRunnerSettings.transport,
                debuggerRunnerSettings.debugPort
            )
                .checkValidity(true)
                .asyncAgent(false) // add manually to allow early tmp folder deletion
                .create(javaCommandLineState.javaParameters)

        AsyncStacksUtils.addDebuggerAgent(javaParameters, testCase.project, true, testCase.testRootDisposable)

        val env = javaCommandLineState.environment

        return testCase.attachVirtualMachine(javaCommandLineState, env, debugParameters, false)
    }
}