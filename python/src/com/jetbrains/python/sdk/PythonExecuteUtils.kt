// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.execution.target.value.constant
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.console.addDefaultEnvironments
import com.jetbrains.python.run.PythonModuleExecution
import com.jetbrains.python.run.buildTargetedCommandLine
import org.jetbrains.annotations.ApiStatus
import java.nio.charset.Charset

@ApiStatus.Internal
object PythonExecuteUtils {
  fun executePyModuleScript(
    project: Project,
    sdk: Sdk,
    pyModuleToRun: String,
    runArgs: List<String>,
    envs: Map<String, String> = emptyMap()
  ): ProcessOutput {
    val targetEnvConfiguration = sdk.targetEnvConfiguration
    val execution = PythonModuleExecution()
    execution.moduleName = pyModuleToRun
    execution.parameters += runArgs.map { constant(it) }

    val patchedEnvs = addDefaultEnvironments(sdk, envs.toMutableMap())
    patchedEnvs.forEach {
      execution.addEnvironmentVariable(it.key, it.value)
    }

    val request = targetEnvConfiguration?.createEnvironmentRequest(project) ?: LocalTargetEnvironmentRequest()
    val targetEnvironment = request.prepareEnvironment(TargetProgressIndicator.EMPTY)

    val targetCommandLine = execution.buildTargetedCommandLine(
      targetEnvironment = targetEnvironment,
      sdk = sdk,
      interpreterParameters = listOf(),
      isUsePty = false,
    )
    val process = targetEnvironment.createProcess(targetCommandLine)
    val processOutput = CapturingProcessHandler(process, Charset.defaultCharset(),
                                                targetCommandLine.getCommandPresentation(targetEnvironment))
      .runProcess(5000, true)
    return processOutput
  }

}