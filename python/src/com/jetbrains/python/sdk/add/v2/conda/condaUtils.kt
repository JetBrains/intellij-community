// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.conda

import com.intellij.python.sdk.common.PyEnvRef
import com.jetbrains.python.sdk.add.v2.addInterpreterByEnvRef
import com.intellij.python.community.impl.conda.CondaPyTool
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.pytools.backend.Version
import com.intellij.python.pytools.backend.getToolVersion
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.conda.savePythonCondaPath
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.PythonAddInterpreterModel
import com.jetbrains.python.sdk.add.v2.pathHolder
import com.jetbrains.python.sdk.add.v2.targetEnvironmentConfiguration
import com.jetbrains.python.sdk.add.v2.validationResult
import com.jetbrains.python.sdk.conda.createCondaSdkAlongWithNewEnv
import com.jetbrains.python.sdk.conda.createCondaSdkFromExistingEnvironment
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.workingDirectory
import kotlinx.coroutines.flow.takeWhile

@RequiresEdt(generateAssertion = false /* IJPL-115548 */)
internal fun PythonAddInterpreterModel<*>.createCondaCommand(): PyResult<PyCondaCommand> {
  val targetEnvironmentConfiguration = fileSystem.targetEnvironmentConfiguration
  val executable = condaViewModel.condaExecutable.get() ?: return PyResult.localizedError(message("python.sdk.select.conda.path.title"))
  val path = executable.pathHolder.getOr { return it }
  return PyCondaCommand(fullCondaPathOnTarget = path.toStringOnRemoteMachine(),
                        targetConfig = targetEnvironmentConfiguration).let { PyResult.success(it) }
}

internal suspend fun PythonAddInterpreterModel<*>.createCondaEnvironment(
  moduleOrProject: ModuleOrProject,
  request: NewCondaEnvRequest,
): PyResult<PythonInterpreter> {

  return createCondaCommand().getOr { return it }.createCondaSdkAlongWithNewEnv(
    moduleOrProject,
    newCondaEnvInfo = request,
    moduleOrProject.workingDirectory ?: return PyResult.localizedError(message("python.sdk.project.working.directory.not.found")),
  )
}

/**
 * Saves [pathHolder] as the custom conda path. Does this only for an EEL path on a local file system.
 */
internal fun PythonAddInterpreterModel<*>.saveCondaPathIfLocal(pathHolder: PathHolder) {
  when (pathHolder) {
    is PathHolder.Eel -> {
      if (fileSystem.isLocal) {
        savePythonCondaPath(pathHolder.path)
      }
    }
    is PathHolder.Target -> Unit
  }
}

internal fun PythonAddInterpreterModel<*>.getBaseCondaOrError(): PyResult<PyCondaEnv> {
  val baseConda = condaViewModel.baseCondaEnv.get()
  return if (baseConda != null) PyResult.success(baseConda) else PyResult.localizedError(message("python.sdk.conda.no.base.env.error"))
}

/**
 * Waits until the environments are loaded. Then returns the base environment if [base] is `true`,
 * or the selected environment if [base] is `false`.
 */
internal suspend fun PythonAddInterpreterModel<*>.getCondaEnvOrError(base: Boolean): PyResult<PyCondaEnv> {
  condaViewModel.condaEnvironmentsLoading.takeWhile { it }.collect { }
  return if (base) {
    getBaseCondaOrError()
  }
  else {
    condaViewModel.selectedCondaEnv.get()?.let { PyResult.success(it) }
    ?: PyResult.localizedError(message("python.sdk.conda.no.env.selected.error"))
  }
}

/**
 * Returns the interpreter for [pyCondaEnv]. Uses an existing SDK if there is one, else creates a new SDK.
 */
internal suspend fun PythonAddInterpreterModel<*>.createSdkFromCondaEnv(
  moduleOrProject: ModuleOrProject,
  pyCondaEnv: PyCondaEnv,
): PyResult<PythonInterpreter> {
  fileSystem.addInterpreterByEnvRef(moduleOrProject, CondaPyTool.getInstance(), PyEnvRef(pyCondaEnv.envIdentity.userReadableName))?.let { return it }
  val executable = condaViewModel.condaExecutable.get() ?: return PyResult.localizedError(message("python.sdk.select.conda.path.title"))
  // We only take pathHolder if everything is valid
  val pathHolder = executable.validationResult.getOr { return it }.pathHolder

  val workingDirectory =
    moduleOrProject.workingDirectory ?: return PyResult.localizedError(message("python.sdk.project.working.directory.not.found"))
  val pythonInterpreter = PyCondaCommand(fullCondaPathOnTarget = pathHolder.toStringOnRemoteMachine(),
                                         targetConfig = fileSystem.targetEnvironmentConfiguration).createCondaSdkFromExistingEnvironment(
    moduleOrProject = moduleOrProject,
    condaIdentity = pyCondaEnv.envIdentity,
    workingDirectory = workingDirectory,
  ).getOr { return it }

  return PyResult.success(pythonInterpreter)
}

suspend fun BinaryToExec.getCondaVersion(): PyResult<Version> = getToolVersion("conda")
