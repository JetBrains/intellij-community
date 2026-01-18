// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.venv

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.impl.venv.createVenv
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.getOr
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.v2.*

suspend fun <P : PathHolder> PythonMutableTargetAddInterpreterModel<P>.setupVirtualenv(venvFolder: P, moduleOrProject: ModuleOrProject): PyResult<Sdk> {
  val baseSdkPath = when (val baseSdk = state.baseInterpreter.get()!!) {
    is InstallableSelectableInterpreter -> installBaseSdk(baseSdk.sdk, this.existingSdks)?.let { fileSystem.wrapSdk(it) }?.homePath
    is ExistingSelectableInterpreter -> baseSdk.homePath
    is DetectedSelectableInterpreter, is ManuallyAddedSelectableInterpreter -> baseSdk.homePath
  }!!

  if (fileSystem.isReadOnly) {
    return PyResult.localizedError(message("the.file.system.is.read.only"))
  }

  val newSdk = createSdkFromBasePython(
    moduleOrProject = moduleOrProject,
    pathToBasePython = baseSdkPath,
    pathToVenvHome = venvFolder,
    existingSdks = existingSdks
  ).getOr { return it }

  return PyResult.success(newSdk.sdk)
}

private suspend fun <P : PathHolder> PythonAddInterpreterModel<P>.createSdkFromBasePython(
  moduleOrProject: ModuleOrProject,
  pathToBasePython: P,
  pathToVenvHome: P,
  existingSdks: List<Sdk>,
): PyResult<SdkWrapper<P>> {
  val basePython = fileSystem.getBinaryToExec(pathToBasePython)
  val inheritSitePackages = venvViewModel.inheritSitePackages.get()
  createVenv(basePython, pathToVenvHome.toString(), inheritSitePackages).getOr(message("project.error.cant.venv")) { return it }

  val venvPythonBinaryPath = fileSystem.resolvePythonBinary(pathToVenvHome)
                             ?: return PyResult.localizedError(message("commandLine.directoryCantBeAccessed", pathToVenvHome))

  val detectedSelectableInterpreter = fileSystem.getSystemPythonFromSelection(venvPythonBinaryPath, requireSystemPython = false).getOr { return it }

  val sdkResult = detectedSelectableInterpreter.setupSdk(
    moduleOrProject = moduleOrProject,
    allSdks = existingSdks,
    fileSystem = fileSystem,
    targetPanelExtension = state.targetPanelExtension.get(),
    isAssociateWithModule = !venvViewModel.makeAvailableForAllProjects.get()
  )

  return when (sdkResult) {
    is com.jetbrains.python.Result.Success -> PyResult.success(fileSystem.wrapSdk(sdkResult.result))
    is com.jetbrains.python.Result.Failure -> PyResult.failure(sdkResult.error)
  }
}
