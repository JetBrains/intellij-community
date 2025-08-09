// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironment.TargetPath
import com.intellij.execution.target.TargetProgressIndicatorAdapter
import com.intellij.execution.target.value.TargetEnvironmentFunction
import com.intellij.execution.target.value.constant
import com.intellij.execution.target.value.targetPath
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.console.addDefaultEnvironments
import com.jetbrains.python.run.PythonCommandLineState.getPythonTargetInterpreter
import com.jetbrains.python.run.PythonModuleExecution
import com.jetbrains.python.run.buildTargetedCommandLine
import com.jetbrains.python.run.ensureProjectSdkAndModuleDirsAreOnTarget
import com.jetbrains.python.statistics.modules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@ApiStatus.Internal
object PythonExecuteUtils {
  suspend fun executePyModuleScript(
    project: Project,
    sdk: Sdk,
    pyModuleToRun: String,
    runArgs: List<String>,
    timeout: Duration = 5.seconds,
    envs: Map<String, String> = emptyMap(),
    workingDir: Path? = null,
  ): ProcessOutput {
    val args = runArgs.map { constant(it) }
    val result = createProcess(project, null, sdk, pyModuleToRun, args, envs, workingDir, additionalUploadLocalDir = null)
    val capturingProcessHandler = CapturingProcessHandler(result.process, Charset.defaultCharset(), result.commandPresentation)
    return withContext(Dispatchers.IO) {
      capturingProcessHandler.runProcess(timeout.inWholeMilliseconds.toInt(), true)
    }
  }

  suspend fun createProcess(
    project: Project,
    module: Module? = null,
    sdk: Sdk,
    pyModuleToRun: String,
    runArgs: List<TargetEnvironmentFunction<String>>,
    envs: Map<String, String>,
    workingDir: Path?,
    additionalUploadLocalDir: Path?,
    targetPortsForwarding: List<Int> = emptyList(),
  ): TargetProcessRunResult {
    val execution = PythonModuleExecution()
    execution.moduleName = pyModuleToRun
    execution.parameters += runArgs
    execution.workingDir = workingDir?.let { targetPath(it) }
    val patchedEnvs = addDefaultEnvironments(sdk, envs.toMutableMap())
    patchedEnvs.forEach {
      execution.addEnvironmentVariable(it.key, it.value)
    }

    val helpersAwareTargetEnvironmentRequest = getPythonTargetInterpreter(project, sdk)
    helpersAwareTargetEnvironmentRequest.preparePyCharmHelpers()
    val request = helpersAwareTargetEnvironmentRequest.targetEnvironmentRequest

    val modules: Array<Module> = module?.let { arrayOf(it) } ?: project.modules
    request.ensureProjectSdkAndModuleDirsAreOnTarget(project, *modules)

    if (additionalUploadLocalDir != null) {
      val uploadVolume = TargetEnvironment.UploadRoot(localRootPath = additionalUploadLocalDir, targetRootPath = TargetPath.Temporary())
      request.uploadVolumes += uploadVolume
    }

    targetPortsForwarding.forEach {
      request.targetPortBindings += TargetEnvironment.TargetPortBinding(null, it)
    }

    val targetEnvironment = withContext(Dispatchers.IO) {
      coroutineToIndicator {
        request.prepareEnvironment(TargetProgressIndicatorAdapter(it))
      }
    }


    val targetCommandLine = execution.buildTargetedCommandLine(
      targetEnvironment = targetEnvironment,
      sdk = sdk,
      interpreterParameters = listOf(),
      isUsePty = false,
    )

    val process = withContext(Dispatchers.IO) {
      coroutineToIndicator {
        targetEnvironment.createProcess(targetCommandLine, it)
      }
    }
    return TargetProcessRunResult(process, targetCommandLine, targetEnvironment)
  }
}