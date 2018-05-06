// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.flavors

import com.intellij.execution.RunCanceledByUserException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import icons.PythonIcons
import org.jetbrains.annotations.SystemDependent
import java.io.File
import javax.swing.Icon

/**
 * @author vlan
 */

const val PIP_FILE: String = "Pipfile"
// TODO: Provide a special icon for pipenv
val PIPENV_ICON: Icon = PythonIcons.Python.PythonClosed

/**
 * Tells if the SDK was added as a pipenv.
 */
var Sdk.isPipEnv: Boolean
  get() = (sdkAdditionalData as? PythonSdkAdditionalData)?.isPipEnv ?: false
  set(value) {
    getOrCreateAdditionalData().isPipEnv = value
  }

/**
 * Finds the pipenv executable in `$PATH`.
 */
fun getPipEnvExecutable(): File? =
  PathEnvironmentVariableUtil.findInPath("pipenv")

/**
 * Sets up the pipenv environment for the specified project path.
 *
 * @return the path to the pipenv environment.
 */
fun setupPipEnv(projectPath: @SystemDependent String, python: String?, installPackages: Boolean): @SystemDependent String {
  when {
    installPackages -> {
      val pythonArgs = if (python != null) listOf("--python", python) else emptyList()
      val command = pythonArgs + listOf("install", "--dev")
      runPipEnv(projectPath, *command.toTypedArray())
    }
    python != null ->
      runPipEnv(projectPath, "--python", python)
    else ->
      runPipEnv(projectPath, "run", "python", "-V")
  }
  return runPipEnv(projectPath, "--venv").trim()
}

/**
 * Runs the configured pipenv for the specified project path.
 */
fun runPipEnv(projectPath: @SystemDependent String, vararg args: String): String {
  val executable = getPipEnvExecutable()?.path ?:
                   throw PyExecutionException("Cannot find pipenv", "pipenv", emptyList(), ProcessOutput())

  val command = listOf(executable) + args
  val commandLine = GeneralCommandLine(command).withWorkDirectory(projectPath)
  val handler = CapturingProcessHandler(commandLine)
  val indicator = ProgressManager.getInstance().progressIndicator
  val result = if (indicator != null) {
    handler.runProcessWithProgressIndicator(indicator)
  }
  else {
    handler.runProcess()
  }
  with(result) {
    return when {
      isCancelled ->
        throw RunCanceledByUserException()
      exitCode != 0 ->
        throw PyExecutionException("Cannot run Python from pipenv", executable, args.asList(),
                                   stdout, stderr, exitCode, emptyList())
      else -> stdout
    }
  }
}
