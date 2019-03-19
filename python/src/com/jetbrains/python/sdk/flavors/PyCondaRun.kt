// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.flavors

import com.intellij.execution.ExecutionException
import com.intellij.execution.RunCanceledByUserException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.python.packaging.PyCondaPackageService
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.sdk.PythonSdkType

@Throws(PyExecutionException::class)
fun runConda(condaExecutable: String, arguments: List<String>): ProcessOutput {
  return run(condaExecutable, arguments, readCondaEnv(condaExecutable))
}

@Throws(PyExecutionException::class)
fun runConda(sdk: Sdk, arguments: List<String>): ProcessOutput {
  return findCondaExecutable(sdk).let { run(it, arguments, sdk.getUserData(PythonSdkType.ENVIRONMENT_KEY) ?: readCondaEnv(it)) }
}

@Throws(PyExecutionException::class)
fun runCondaPython(condaPythonExecutable: String, arguments: List<String>): ProcessOutput {
  return run(condaPythonExecutable, arguments, PythonSdkType.activateVirtualEnv(condaPythonExecutable))
}

private fun run(executable: String, arguments: List<String>, env: Map<String, String>?): ProcessOutput {
  val commandLine = GeneralCommandLine(executable).withParameters(arguments).withEnvironment(env)
  val indicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
  return CapturingProcessHandler(commandLine).runProcessWithProgressIndicator(indicator).apply {
    if (isCancelled) throw RunCanceledByUserException()
    checkExitCode(executable, arguments)
  }
}

private fun readCondaEnv(condaExecutable: String): Map<String, String>? {
  return PyCondaPackageService.getCondaBasePython(condaExecutable)?.let { PythonSdkType.activateVirtualEnv(it) }
}

@Throws(ExecutionException::class)
private fun findCondaExecutable(sdk: Sdk): String {
  return PyCondaPackageService.getCondaExecutable(sdk.homePath) ?: throw ExecutionException("Cannot find conda executable")
}

@Throws(PyExecutionException::class)
private fun ProcessOutput.checkExitCode(executable: String, arguments: List<String>) {
  if (exitCode != 0) {
    val message = if (StringUtil.isEmptyOrSpaces(stdout) && StringUtil.isEmptyOrSpaces(stderr)) "Permission denied"
    else "Non-zero exit code"
    throw PyExecutionException(message, executable, arguments, this)
  }
}