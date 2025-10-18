// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.venv

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.impl.venv.createVenv
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.getOr
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.add.v2.*

suspend fun <P : PathHolder> PythonMutableTargetAddInterpreterModel<P>.setupVirtualenv(venvFolder: P, moduleOrProject: ModuleOrProject?): PyResult<Sdk> {
  val baseSdkPath = when (val baseSdk = state.baseInterpreter.get()!!) {
    is InstallableSelectableInterpreter -> installBaseSdk(baseSdk.sdk, this.existingSdks)?.let { fileSystem.wrapSdk(it) }?.homePath
    is ExistingSelectableInterpreter -> baseSdk.homePath
    is DetectedSelectableInterpreter, is ManuallyAddedSelectableInterpreter -> baseSdk.homePath
  }!!

  if (fileSystem.isReadOnly) {
    return PyResult.localizedError(message("the.file.system.is.read.only"))
  }

  val newSdk = createSdkFromBasePython(
    project = moduleOrProject?.project,
    pathToBasePython = baseSdkPath,
    pathToVenvHome = venvFolder,
    inheritSitePackages = venvState.inheritSitePackages.get(),
    existingSdks = existingSdks
  ).getOr { return it }

  // todo check exclude
  val module = PyProjectCreateHelpers.getModule(moduleOrProject, null)

  if (module != null) {
    module.excludeInnerVirtualEnv(newSdk.sdk)
    if (!this.venvState.makeAvailableForAllProjects.get()) {
      newSdk.sdk.setAssociationToModule(module)
    }
  }

  return PyResult.success(newSdk.sdk)
}

private suspend fun <P : PathHolder> PythonAddInterpreterModel<P>.createSdkFromBasePython(
  project: Project?,
  pathToBasePython: P,
  pathToVenvHome: P,
  inheritSitePackages: Boolean,
  existingSdks: List<Sdk>,
): PyResult<SdkWrapper<P>> {
  val basePython = fileSystem.getBinaryToExec(pathToBasePython)
  createVenv(basePython, pathToVenvHome.toString(), inheritSitePackages).getOr(message("project.error.cant.venv")) { return it }

  val pythonBinaryPath = fileSystem.resolvePythonBinary(pathToVenvHome)
                         ?: return PyResult.localizedError(message("commandLine.directoryCantBeAccessed", pathToVenvHome))

  val detectedSelectableInterpreter = fileSystem.getSystemPythonFromSelection(pythonBinaryPath).getOr { return it }

  val sdkResult = detectedSelectableInterpreter.setupSdk(
    project = project,
    allSdks = existingSdks,
    fileSystem = fileSystem,
    targetPanelExtension = state.targetPanelExtension.get()
  )

  return sdkResult.mapSuccess { sdk -> fileSystem.wrapSdk(sdk) }
}
