// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.flavors

import com.google.gson.annotations.SerializedName
import com.intellij.execution.ExecutionException
import com.intellij.execution.RunCanceledByUserException
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.packaging.IndicatedProcessOutputListener
import com.jetbrains.python.packaging.PyCondaPackageService
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.run.PythonCommandLineState
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import com.jetbrains.python.sdk.PySdkUtil
import com.jetbrains.python.target.PyTargetAwareAdditionalData

@Deprecated("Do not run conda directly, use  configureBuilderToRunPythonOnTarget")
@Throws(ExecutionException::class)
fun runCondaOnTarget(targetEnvironmentRequest: TargetEnvironmentRequest, condaExecutable: String, arguments: List<String>): ProcessOutput {
  return runOnTarget(targetEnvironmentRequest, condaExecutable, arguments, readCondaEnv(condaExecutable))
}


@Deprecated("Do not run conda directly, use  configureBuilderToRunPythonOnTarget")
private fun runOnTarget(targetEnvironmentRequest: TargetEnvironmentRequest,
                        executable: String,
                        arguments: List<String>,
                        env: Map<String, String>?): ProcessOutput {
  // TODO [targets] Use the wrapper for `ProgressIndicator`
  val targetProgressIndicator = TargetProgressIndicator.EMPTY
  val targetEnvironment = targetEnvironmentRequest.prepareEnvironment(targetProgressIndicator)
  val targetedCommandLineBuilder = TargetedCommandLineBuilder(targetEnvironmentRequest).apply {
    setExePath(executable)
    addParameters(arguments)
    env?.forEach { (key, value) -> addEnvironmentVariable(key, value) }
  }
  val targetedCommandLine = targetedCommandLineBuilder.build()
  val indicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
  val process = targetEnvironment.createProcess(targetedCommandLine, indicator)
  val commandPresentation = targetedCommandLine.getCommandPresentation(targetEnvironment)
  val handler = CapturingProcessHandler(process, Charsets.UTF_8, commandPresentation).apply {
    addProcessListener(IndicatedProcessOutputListener(indicator))
  }
  return handler.runProcessWithProgressIndicator(indicator).apply {
    if (isCancelled) throw RunCanceledByUserException()
    checkExitCode(executable, arguments)
  }
}

@Deprecated("Do not run conda directly, use  configureBuilderToRunPythonOnTarget")
private fun readCondaEnv(condaExecutable: String): Map<String, String>? {
  return PyCondaPackageService.getCondaBasePython(condaExecutable)?.let { PySdkUtil.activateVirtualEnv(it) }
}

@Throws(PyExecutionException::class)
private fun ProcessOutput.checkExitCode(executable: String, arguments: List<String>) {
  if (exitCode != 0) {
    val message = if (StringUtil.isEmptyOrSpaces(stdout) && StringUtil.isEmptyOrSpaces(stderr))
      PySdkBundle.message("python.conda.permission.denied")
    else PySdkBundle.message("python.conda.non.zero.exit.code")
    throw PyExecutionException(message, executable, arguments, this)
  }
}
