// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.ExecutionException
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withModalProgress
import com.jetbrains.extensions.failure
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.VirtualEnvReader
import com.jetbrains.python.sdk.conda.createCondaSdkFromExistingEnv
import com.jetbrains.python.sdk.excludeInnerVirtualEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path


// todo should it be overriden for targets?
suspend fun PythonMutableTargetAddInterpreterModel.setupVirtualenv(venvPath: Path, projectPath: Path): Result<Sdk> {
  val baseSdk = state.baseInterpreter.get()!!


  val baseSdkPath = Path.of(when (baseSdk) {
                              is InstallableSelectableInterpreter -> installBaseSdk(baseSdk.sdk, this.existingSdks)?.homePath // todo handle errors
                              is ExistingSelectableInterpreter -> baseSdk.sdk.homePath
                              is DetectedSelectableInterpreter, is ManuallyAddedSelectableInterpreter -> baseSdk.homePath
                            }!!)


  try {
    createVirtualenv(baseSdkPath,
                     venvPath,
                     projectPath,
                     inheritSitePackages = state.inheritSitePackages.get())
  }
  catch (e: ExecutionException) {
    return Result.failure(e)
  }

  if (targetEnvironmentConfiguration != null) error("Remote targets aren't supported")
  val venvPython = VirtualEnvReader.Instance.findPythonInPythonRoot(venvPath)
  if (venvPython == null) {
    return failure(message("commandLine.directoryCantBeAccessed", venvPath))
  }

  val homeFile = try {
    // refresh needs write action
    writeAction {
      VfsUtil.findFile(venvPython, true)
    }
  }
  catch (e: ExecutionException) {
    return Result.failure(e)
  }
  if (homeFile == null) {
    return failure(message("commandLine.directoryCantBeAccessed", venvPath))
  }

  val newSdk = createSdk(homeFile, projectPath, existingSdks.toTypedArray())

  // todo check exclude
  ProjectManager.getInstance().openProjects
    .firstNotNullOfOrNull {
      withContext(Dispatchers.IO) {
        ModuleUtil.findModuleForFile(homeFile, it)
      }
    }
    ?.excludeInnerVirtualEnv(newSdk)
  return Result.success(newSdk)

}


// todo rewrite this
/**
 * [base] or selected
 */
suspend fun PythonAddInterpreterModel.selectCondaEnvironment(base: Boolean): Result<Sdk> {
  val identity = if (base) {
    getBaseCondaOrError()
  }
  else {
    state.selectedCondaEnv.get()?.let { Result.success(it) } ?: failure(message("python.sdk.conda.no.env.selected.error"))
  }
    .getOrElse { return Result.failure(it) }
    .envIdentity
  val existingSdk = ProjectJdkTable.getInstance().findJdk(identity.userReadableName)
  if (existingSdk != null && isCondaSdk(existingSdk)) return Result.success(existingSdk)

  val sdk = withModalProgress(ModalTaskOwner.guess(),
                              message("sdk.create.custom.conda.create.progress"),
                              TaskCancellation.nonCancellable()) {
    //PyCondaCommand(condaExecutableOnTarget, targetConfig = targetEnvironmentConfiguration)
    PyCondaCommand(state.condaExecutable.get(), targetConfig = targetEnvironmentConfiguration).createCondaSdkFromExistingEnv(identity,
                                                                                                                             this@selectCondaEnvironment.existingSdks,
                                                                                                                             ProjectManager.getInstance().defaultProject)
  }

  (sdk.sdkType as PythonSdkType).setupSdkPaths(sdk)
  addSdk(sdk)
  return Result.success(sdk)
}


internal fun PythonAddInterpreterModel.installPythonIfNeeded(interpreter: PythonSelectableInterpreter): String? {
  // todo use target config
  return if (interpreter is InstallableSelectableInterpreter) {
    installBaseSdk(interpreter.sdk, existingSdks)?.homePath ?: return null
  }
  else interpreter.homePath
}