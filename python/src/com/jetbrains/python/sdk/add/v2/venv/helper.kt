// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.venv

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.python.venv.createVenv
import com.intellij.python.venv.MINIMUM_SUPPORTED_VENV_PYTHON_VERSION
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.getOr
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.v2.DetectedSelectableInterpreter
import com.jetbrains.python.sdk.add.v2.ExistingSelectableInterpreter
import com.jetbrains.python.sdk.add.v2.InstallableSelectableInterpreter
import com.jetbrains.python.sdk.add.v2.ManuallyAddedSelectableInterpreter
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.PythonAddInterpreterModel
import com.jetbrains.python.sdk.add.v2.PythonMutableTargetAddInterpreterModel
import com.jetbrains.python.sdk.add.v2.PythonSelectableInterpreter
import com.jetbrains.python.sdk.add.v2.SdkWrapper
import com.jetbrains.python.sdk.add.v2.installBaseSdk
import com.jetbrains.python.sdk.add.v2.setupSdk

/**
 * A venv base interpreter must be Python 3.8+ (see createVenv and the bundled `virtualenv-py3.pyz`).
 * Returns an error for older interpreters so the dialog can flag the selection invalid and disable its
 * action button, or `null` when the interpreter is acceptable.
 */
internal fun venvBaseVersionError(interpreter: PythonSelectableInterpreter<*>): ValidationInfo? {
  val level = interpreter.pythonInfo.languageLevel
  return if (level.isAtLeast(MINIMUM_SUPPORTED_VENV_PYTHON_VERSION)) null
  else ValidationInfo(message("sdk.create.custom.venv.unsupported.base.version", MINIMUM_SUPPORTED_VENV_PYTHON_VERSION.toPythonVersion()))
}

/**
 * For selecting an existing interpreter: Python < 3.8 is still usable, but the IDE no longer provides
 * pip/setuptools for it (see PipManagementInstaller). Returns a non-blocking warning reminding the user
 * to have them installed, or `null` for supported versions.
 */
internal fun unsupportedPythonManagementWarning(interpreter: PythonSelectableInterpreter<*>): ValidationInfo? {
  val level = interpreter.pythonInfo.languageLevel
  return if (level.isAtLeast(MINIMUM_SUPPORTED_VENV_PYTHON_VERSION)) null
  // asWarning() alone keeps okEnabled = false (the ValidationInfo default), which would still disable OK;
  // withOKEnabled() makes it a true non-blocking warning.
  else ValidationInfo(message("sdk.create.existing.unsupported.python.management.warning", MINIMUM_SUPPORTED_VENV_PYTHON_VERSION.toPythonVersion())).asWarning().withOKEnabled()
}

internal suspend fun <P : PathHolder> PythonMutableTargetAddInterpreterModel<P>.setupVirtualenv(
  venvFolder: P,
  moduleOrProject: ModuleOrProject,
): PyResult<Sdk> {
  val baseSdkPath = when (val baseSdk = state.baseInterpreter.get()!!) {
    is InstallableSelectableInterpreter -> installBaseSdk(baseSdk.installableSdk)?.let { fileSystem.wrapSdk(it) }?.homePath
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
  ).getOr { return it }

  return PyResult.success(newSdk.sdk)
}

private suspend fun <P : PathHolder> PythonAddInterpreterModel<P>.createSdkFromBasePython(
  moduleOrProject: ModuleOrProject,
  pathToBasePython: P,
  pathToVenvHome: P,
): PyResult<SdkWrapper<P>> {
  val basePython = fileSystem.getBinaryToExec(pathToBasePython)
  val inheritSitePackages = venvViewModel.inheritSitePackages.get()
  createVenv(basePython, pathToVenvHome.toString(), inheritSitePackages).getOr(message("project.error.cant.venv")) { return it }

  val venvPythonBinaryPath = fileSystem.resolvePythonBinary(pathToVenvHome)
                             ?: return PyResult.localizedError(message("commandLine.directoryCantBeAccessed", pathToVenvHome))

  val detectedSelectableInterpreter = fileSystem.getSystemPythonFromSelection(venvPythonBinaryPath, requireSystemPython = false).getOr { return it }

  val sdkResult = detectedSelectableInterpreter.setupSdk(
    moduleOrProject = moduleOrProject,
    fileSystem = fileSystem,
    targetPanelExtension = state.targetPanelExtension.get(),
    isAssociateWithModule = !venvViewModel.makeAvailableForAllProjects.get()
  )

  return when (sdkResult) {
    is com.jetbrains.python.Result.Success -> PyResult.success(fileSystem.wrapSdk(sdkResult.result))
    is com.jetbrains.python.Result.Failure -> PyResult.failure(sdkResult.error)
  }
}
