// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.conda

import com.intellij.openapi.application.EDT
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.isCondaVirtualEnv
import com.jetbrains.python.onSuccess
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.add.v2.*
import com.jetbrains.python.sdk.conda.*
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.takeWhile

@RequiresEdt
internal fun PythonAddInterpreterModel<*>.createCondaCommand(): PyResult<PyCondaCommand> {
  val targetEnvironmentConfiguration = (fileSystem as? FileSystem.Target)?.targetEnvironmentConfiguration
  val executable = condaViewModel.condaExecutable.get() ?: return PyResult.localizedError(message("python.sdk.select.conda.path.title"))
  return PyCondaCommand(
    fullCondaPathOnTarget = executable.pathHolder.toString().convertToPathOnTarget(targetEnvironmentConfiguration),
    targetConfig = targetEnvironmentConfiguration
  ).let { PyResult.success(it) }
}

internal suspend fun PythonAddInterpreterModel<*>.createCondaEnvironment(moduleOrProject: ModuleOrProject, request: NewCondaEnvRequest): PyResult<Sdk> {

  val result = createCondaCommand().getOr { return it }.createCondaSdkAlongWithNewEnv(
    newCondaEnvInfo = request,
    uiContext = Dispatchers.EDT,
    existingSdks = existingSdks,
    project = moduleOrProject.project
  )
    .onSuccess { sdk ->
      (sdk.sdkType as PythonSdkType).setupSdkPaths(sdk)

      val module = PyProjectCreateHelpers.getModule(moduleOrProject, null)
      if (module != null) {
        sdk.setAssociationToModule(module)
      }
      sdk.persist()
    }

  return result
}

internal fun PythonAddInterpreterModel<*>.getBaseCondaOrError(): PyResult<PyCondaEnv> {
  val baseConda = condaViewModel.baseCondaEnv.get()
  return if (baseConda != null) PyResult.success(baseConda) else PyResult.localizedError(message("python.sdk.conda.no.base.env.error"))
}

/**
 * [base] or selected
 */
suspend fun PythonAddInterpreterModel<*>.selectCondaEnvironment(moduleOrProject: ModuleOrProject, base: Boolean): PyResult<Sdk> {
  condaViewModel.condaEnvironmentsLoading.takeWhile { it }.collect { }
  val pyCondaEnv = if (base) {
    getBaseCondaOrError()
  }
  else {
    condaViewModel.selectedCondaEnv.get()?.let { PyResult.success(it) }
    ?: PyResult.localizedError(message("python.sdk.conda.no.env.selected.error"))
  }
    .getOr { return it }
  val existingSdk = ProjectJdkTable.getInstance().findJdk(pyCondaEnv.envIdentity.userReadableName)
  if (existingSdk != null && existingSdk.isCondaVirtualEnv) return PyResult.success(existingSdk)
  val executable = condaViewModel.condaExecutable.get() ?: return PyResult.localizedError(message("python.sdk.select.conda.path.title"))
  executable.validationResult.getOr { return it }

  val sdk = PyCondaCommand(
    fullCondaPathOnTarget = executable.pathHolder.toString(),
    targetConfig = (fileSystem as? FileSystem.Target)?.targetEnvironmentConfiguration
  ).createCondaSdkFromExistingEnv(
    condaIdentity = pyCondaEnv.envIdentity,
    existingSdks = this@selectCondaEnvironment.existingSdks,
    project = moduleOrProject.project,
  )

  (sdk.sdkType as PythonSdkType).setupSdkPaths(sdk)
  PyProjectCreateHelpers.getModule(moduleOrProject, null)?.let { sdk.setAssociationToModule(it) }
  sdk.persist()
  return PyResult.success(sdk)
}

suspend fun BinaryToExec.getCondaVersion(): PyResult<Version> {
  val version = getToolVersion("conda").getOr { return it }
  return try {
    PyResult.success(version)
  }
  catch (ex: VersionFormatException) {
    PyResult.localizedError(ex.localizedMessage)
  }
}