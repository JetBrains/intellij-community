// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.community.services.systemPython.createVenvFromSystemPython
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.getOr
import com.jetbrains.python.isCondaVirtualEnv
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.conda.createCondaSdkFromExistingEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import kotlinx.coroutines.flow.first
import java.nio.file.Path


// todo should it be overriden for targets?
suspend fun PythonMutableTargetAddInterpreterModel.setupVirtualenv(venvPath: Path, moduleOrProject: ModuleOrProject?): PyResult<Sdk> {
  val baseSdk = state.baseInterpreter.get()!!

  val baseSdkPath = Path.of(when (baseSdk) {
                              is InstallableSelectableInterpreter -> installBaseSdk(baseSdk.sdk, this.existingSdks)?.homePath // todo handle errors
                              is ExistingSelectableInterpreter -> baseSdk.sdk.homePath
                              is DetectedSelectableInterpreter, is ManuallyAddedSelectableInterpreter -> baseSdk.homePath
                            }!!)

  val systemPython = SystemPythonService().registerSystemPython(baseSdkPath).getOr(message("sdk.create.error.base.broken", baseSdkPath)) { return it }


  val venvPython = createVenvFromSystemPython(systemPython, venvPath, inheritSitePackages = state.inheritSitePackages.get()).getOr(message("project.error.cant.venv")) { return it }

  if (targetEnvironmentConfiguration != null) {
    error("Remote targets aren't supported")
  }

  val homeFile =
    // refresh needs write action
    edtWriteAction {
      VfsUtil.findFile(venvPython, true)
    }
  if (homeFile == null) {
    return PyResult.localizedError(message("commandLine.directoryCantBeAccessed", venvPython))
  }

  val newSdk = createSdk(homeFile, projectPathFlows.projectPathWithDefault.first(), existingSdks.toTypedArray())

  // todo check exclude
  val module = PyProjectCreateHelpers.getModule(moduleOrProject, homeFile)


  if (module != null) {
    module.excludeInnerVirtualEnv(newSdk)
    if (!this.state.makeAvailableForAllProjects.get()) {
      newSdk.setAssociationToModule(module)
    }
  }

  return PyResult.success(newSdk)
}


// todo rewrite this
/**
 * [base] or selected
 */
suspend fun PythonAddInterpreterModel.selectCondaEnvironment(base: Boolean): PyResult<Sdk> {
  val identity = if (base) {
    getBaseCondaOrError()
  }
  else {
    state.selectedCondaEnv.get()?.let { PyResult.success(it) } ?: PyResult.localizedError(message("python.sdk.conda.no.env.selected.error"))
  }
    .getOr { return it }
    .envIdentity
  val existingSdk = ProjectJdkTable.getInstance().findJdk(identity.userReadableName)
  if (existingSdk != null && existingSdk.isCondaVirtualEnv) return PyResult.success(existingSdk)

  val sdk = PyCondaCommand(
    fullCondaPathOnTarget = state.condaExecutable.get(),
    targetConfig = targetEnvironmentConfiguration
  ).createCondaSdkFromExistingEnv(
    condaIdentity = identity,
    existingSdks = this@selectCondaEnvironment.existingSdks,
    project = ProjectManager.getInstance().defaultProject
  )

  (sdk.sdkType as PythonSdkType).setupSdkPaths(sdk)
  sdk.persist()
  return PyResult.success(sdk)
}


internal fun PythonAddInterpreterModel.installPythonIfNeeded(interpreter: PythonSelectableInterpreter): Path? {
  // todo use target config
  val pathString = if (interpreter is InstallableSelectableInterpreter) {
    installBaseSdk(interpreter.sdk, existingSdks)?.homePath
  }
  else interpreter.homePath
  return pathString?.let { Path.of(it) }
}