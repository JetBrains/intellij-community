// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.flavors

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
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
import com.jetbrains.python.packaging.PyPackageManagerImpl
import com.jetbrains.python.sdk.PythonSdkType

@Throws(ExecutionException::class)
fun runConda(condaExecutable: String, arguments: List<String>): ProcessOutput {
  return run(condaExecutable, arguments, readCondaEnv(condaExecutable))
}

@Throws(ExecutionException::class)
fun runConda(sdk: Sdk?, arguments: List<String>): ProcessOutput {
  val condaExecutable = findCondaExecutable(sdk)
  val environment = if (sdk != null) {
    PythonSdkType.activateVirtualEnv(sdk)
  }
  else {
    readCondaEnv(condaExecutable)
  }
  return run(condaExecutable, arguments, environment)
}

@Throws(ExecutionException::class)
fun runCondaPython(condaPythonExecutable: String, arguments: List<String>): ProcessOutput {
  return run(condaPythonExecutable, arguments, PythonSdkType.activateVirtualEnv(condaPythonExecutable))
}

private fun run(executable: String, arguments: List<String>, env: Map<String, String>?): ProcessOutput {
  val commandLine = GeneralCommandLine(executable).withParameters(arguments).withEnvironment(env)
  val indicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
  val handler = CapturingProcessHandler(commandLine).apply {
    addProcessListener(PyPackageManagerImpl.IndicatedProcessOutputListener(indicator))
  }
  return handler.runProcessWithProgressIndicator(indicator).apply {
    if (isCancelled) throw RunCanceledByUserException()
    checkExitCode(executable, arguments)
  }
}

private fun readCondaEnv(condaExecutable: String): Map<String, String>? {
  return PyCondaPackageService.getCondaBasePython(condaExecutable)?.let { PythonSdkType.activateVirtualEnv(it) }
}

@Throws(ExecutionException::class)
private fun findCondaExecutable(sdk: Sdk?): String {
  return PyCondaPackageService.getCondaExecutable(sdk?.homePath) ?: throw ExecutionException("Cannot find conda executable")
}

@Throws(PyExecutionException::class)
private fun ProcessOutput.checkExitCode(executable: String, arguments: List<String>) {
  if (exitCode != 0) {
    val message = if (StringUtil.isEmptyOrSpaces(stdout) && StringUtil.isEmptyOrSpaces(stderr)) "Permission denied"
    else "Non-zero exit code"
    throw PyExecutionException(message, executable, arguments, this)
  }
}

@Throws(ExecutionException::class, JsonSyntaxException::class)
fun listCondaEnvironments(sdk: Sdk?): List<String> {
  val output = runConda(sdk, listOf("env", "list", "--json"))
  val text = output.stdout
  val envList = Gson().fromJson(text, CondaEnvironmentsList::class.java)
  return envList.envs
}

private data class CondaEnvironmentsList(@SerializedName("envs") var envs: List<String>)
