// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.ExecutionException
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.VirtualEnvReader
import com.jetbrains.python.sdk.add.target.conda.createCondaSdkFromExistingEnv
import com.jetbrains.python.sdk.excludeInnerVirtualEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.jetbrains.python.sdk.suggestAssociatedSdkName
import java.nio.file.Path


// todo should it be overriden for targets?
internal fun PythonMutableTargetAddInterpreterModel.setupVirtualenv(venvPath: Path, projectPath: Path, baseSdk: PythonSelectableInterpreter): Result<Sdk> {

  val venvPathOnTarget = venvPath.convertToPathOnTarget(targetEnvironmentConfiguration)

  val baseSdkPath = when (baseSdk) {
    is InstallableSelectableInterpreter -> installBaseSdk(baseSdk.sdk, this.existingSdks)?.homePath // todo handle errors
    is ExistingSelectableInterpreter -> baseSdk.sdk.homePath
    is DetectedSelectableInterpreter, is ManuallyAddedSelectableInterpreter -> baseSdk.homePath
    else -> error("Unknown interpreter")
  }


  createVirtualenv(baseSdkPath!!,
                   venvPathOnTarget,
                   projectPath,
                   inheritSitePackages = state.inheritSitePackages.get())

  if (targetEnvironmentConfiguration != null) error("Remote targets aren't supported")
  val venvPython =  VirtualEnvReader.Instance.findPythonInPythonRoot(Path.of(venvPathOnTarget))?.toString()

  val homeFile = try {
    StandardFileSystems.local().refreshAndFindFileByPath(venvPython!!)!!
  }
  catch (e: ExecutionException) {
    return Result.failure(e)
  }

  val suggestedName = /*suggestedSdkName ?:*/ suggestAssociatedSdkName(homeFile.path, projectPath.toString())
  val newSdk = SdkConfigurationUtil.setupSdk(existingSdks.toTypedArray(), homeFile,
                                             PythonSdkType.getInstance(),
                                             false, null, suggestedName)

  SdkConfigurationUtil.addSdk(newSdk!!)

  // todo check exclude
  ProjectManager.getInstance().openProjects
    .firstNotNullOfOrNull { ModuleUtil.findModuleForFile(homeFile, it) }
    ?.excludeInnerVirtualEnv(newSdk)
  return Result.success(newSdk)

}


// todo rewrite this
internal fun PythonAddInterpreterModel.selectCondaEnvironment(identity: PyCondaEnvIdentity): Sdk {
  val existingSdk = ProjectJdkTable.getInstance().findJdk(identity.userReadableName)
  if (existingSdk != null && isCondaSdk(existingSdk)) return existingSdk

  val sdk = runWithModalProgressBlocking(ModalTaskOwner.guess(),
                                         PyBundle.message("sdk.create.custom.conda.create.progress"),
                                         TaskCancellation.nonCancellable()) {
    //PyCondaCommand(condaExecutableOnTarget, targetConfig = targetEnvironmentConfiguration)
    PyCondaCommand(state.condaExecutable.get(), targetConfig = targetEnvironmentConfiguration).createCondaSdkFromExistingEnv(identity,
                                                                                                                             this@selectCondaEnvironment.existingSdks,
                                                                                                                             ProjectManager.getInstance().defaultProject)
  }

  (sdk.sdkType as PythonSdkType).setupSdkPaths(sdk)
  SdkConfigurationUtil.addSdk(sdk)
  return sdk
}


internal fun PythonAddInterpreterModel.installPythonIfNeeded(interpreter: PythonSelectableInterpreter): String? {
  // todo use target config
  return if (interpreter is InstallableSelectableInterpreter) {
    installBaseSdk(interpreter.sdk, existingSdks)?.homePath ?: return null
  }
  else interpreter.homePath
}