// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.add.target.conda.TargetCommandExecutor
import com.jetbrains.python.sdk.add.target.conda.TargetEnvironmentRequestCommandExecutor
import com.jetbrains.python.sdk.add.target.conda.createCondaSdkAlongWithNewEnv
import com.jetbrains.python.sdk.add.target.conda.createCondaSdkFromExistingEnv
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.jetbrains.python.sdk.flavors.conda.PyCondaFlavorData
import kotlinx.coroutines.Dispatchers

internal val PythonAddInterpreterPresenter.condaExecutableOnTarget: String
  @RequiresEdt get() = state.condaExecutable.get().convertToPathOnTarget(targetEnvironmentConfiguration)

@RequiresEdt
internal fun PythonAddInterpreterPresenter.createCondaCommand(): PyCondaCommand =
  PyCondaCommand(condaExecutableOnTarget, targetConfig = targetEnvironmentConfiguration)

@RequiresEdt
internal fun PythonAddInterpreterPresenter.createCondaEnvironment(request: NewCondaEnvRequest): Sdk? {
  val sdk = runWithModalProgressBlocking(ModalTaskOwner.guess(),
                                         PyBundle.message("sdk.create.custom.conda.create.progress"),
                                         TaskCancellation.nonCancellable()) {
    createCondaCommand()
      .createCondaSdkAlongWithNewEnv(request,
                                     Dispatchers.EDT,
                                     state.allExistingSdks.get(),
                                     ProjectManager.getInstance().defaultProject).getOrNull()
  } ?: return null

  (sdk.sdkType as PythonSdkType).setupSdkPaths(sdk)
  SdkConfigurationUtil.addSdk(sdk)
  return sdk
}

@RequiresEdt
internal fun PythonAddInterpreterPresenter.selectCondaEnvironment(identity: PyCondaEnvIdentity): Sdk {
  val existingSdk = ProjectJdkTable.getInstance().findJdk(identity.userReadableName)
  if (existingSdk != null && isCondaSdk(existingSdk)) return existingSdk

  val sdk = runWithModalProgressBlocking(ModalTaskOwner.guess(),
                                         PyBundle.message("sdk.create.custom.conda.create.progress"),
                                         TaskCancellation.nonCancellable()) {
    createCondaCommand().createCondaSdkFromExistingEnv(identity,
                                                       state.allExistingSdks.get(),
                                                       ProjectManager.getInstance().defaultProject)
  }

  (sdk.sdkType as PythonSdkType).setupSdkPaths(sdk)
  SdkConfigurationUtil.addSdk(sdk)
  return sdk
}

private fun isCondaSdk(sdk: Sdk): Boolean = (sdk.sdkAdditionalData as? PythonSdkAdditionalData)?.flavorAndData?.data is PyCondaFlavorData

@RequiresEdt
internal fun PythonAddInterpreterPresenter.createExecutor(): TargetCommandExecutor = targetEnvironmentConfiguration.toExecutor()

internal fun TargetEnvironmentConfiguration?.toExecutor(): TargetCommandExecutor {
  return TargetEnvironmentRequestCommandExecutor(this?.createEnvironmentRequest(project = null) ?: LocalTargetEnvironmentRequest())
}