// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.python.community.impl.venv.createVenv
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.failure
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.conda.createCondaSdkFromExistingEnv
import com.jetbrains.python.sdk.conda.isConda
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path


// todo should it be overriden for targets?
suspend fun PythonMutableTargetAddInterpreterModel.setupVirtualenv(venvPath: Path, projectPath: Path, moduleOrProject: ModuleOrProject?): com.jetbrains.python.Result<Sdk, PyError> {
  val baseSdk = state.baseInterpreter.get()!!


  val baseSdkPath = Path.of(when (baseSdk) {
                              is InstallableSelectableInterpreter -> installBaseSdk(baseSdk.sdk, this.existingSdks)?.homePath // todo handle errors
                              is ExistingSelectableInterpreter -> baseSdk.sdk.homePath
                              is DetectedSelectableInterpreter, is ManuallyAddedSelectableInterpreter -> baseSdk.homePath
                            }!!)


  val venvPython = createVenv(baseSdkPath, venvPath, inheritSitePackages = state.inheritSitePackages.get()).getOr { return it }

  if (targetEnvironmentConfiguration != null) {
    error("Remote targets aren't supported")
  }

  val homeFile =
    // refresh needs write action
    edtWriteAction {
      VfsUtil.findFile(venvPython, true)
    }
  if (homeFile == null) {
    return com.jetbrains.python.errorProcessing.failure(message("commandLine.directoryCantBeAccessed", venvPython))
  }

  val newSdk = createSdk(homeFile, projectPath, existingSdks.toTypedArray())

  // todo check exclude
  val module = when (moduleOrProject) {
    is ModuleOrProject.ModuleAndProject -> moduleOrProject.module
    is ModuleOrProject.ProjectOnly -> {
      withContext(Dispatchers.IO) {
        ModuleUtil.findModuleForFile(homeFile, moduleOrProject.project)
      }
    }
    null -> null
  }


  if (module != null) {
    module.excludeInnerVirtualEnv(newSdk)
    if (!this.state.makeAvailableForAllProjects.get()) {
      newSdk.setAssociationToModuleAsync(module)
    }
  }

  return com.jetbrains.python.Result.success(newSdk)
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
  if (existingSdk != null && existingSdk.isConda()) return Result.success(existingSdk)

  val sdk = withModalProgress(ModalTaskOwner.guess(),
                              message("sdk.create.custom.conda.create.progress"),
                              TaskCancellation.nonCancellable()) {
    //PyCondaCommand(condaExecutableOnTarget, targetConfig = targetEnvironmentConfiguration)
    PyCondaCommand(state.condaExecutable.get(), targetConfig = targetEnvironmentConfiguration).createCondaSdkFromExistingEnv(identity,
                                                                                                                             this@selectCondaEnvironment.existingSdks,
                                                                                                                             ProjectManager.getInstance().defaultProject)
  }

  (sdk.sdkType as PythonSdkType).setupSdkPaths(sdk)
  sdk.persist()
  return Result.success(sdk)
}


internal fun PythonAddInterpreterModel.installPythonIfNeeded(interpreter: PythonSelectableInterpreter): String? {
  // todo use target config
  return if (interpreter is InstallableSelectableInterpreter) {
    installBaseSdk(interpreter.sdk, existingSdks)?.homePath ?: return null
  }
  else interpreter.homePath
}