// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.ProjectManager
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
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.flavors.conda.PyCondaFlavorData
import kotlinx.coroutines.Dispatchers

@RequiresEdt
internal fun PythonAddInterpreterModel.createCondaCommand(): PyCondaCommand =
  PyCondaCommand(state.condaExecutable.get().convertToPathOnTarget(targetEnvironmentConfiguration),
                 targetConfig = targetEnvironmentConfiguration)

@RequiresEdt
internal fun PythonAddInterpreterModel.createCondaEnvironment(request: NewCondaEnvRequest): Sdk {
  val project = ProjectManager.getInstance().defaultProject
  val existingSdks = this@createCondaEnvironment.existingSdks
  val sdk = runWithModalProgressBlocking(ModalTaskOwner.guess(),
                                         PyBundle.message("sdk.create.custom.conda.create.progress"),
                                         TaskCancellation.nonCancellable()) {
    createCondaCommand()
      .createCondaSdkAlongWithNewEnv(request,
                                     Dispatchers.EDT,
                                     existingSdks,
                                     project).getOrThrow()
  }

  (sdk.sdkType as PythonSdkType).setupSdkPaths(sdk)
  SdkConfigurationUtil.addSdk(sdk)
  return sdk
}


internal fun isCondaSdk(sdk: Sdk): Boolean = (sdk.sdkAdditionalData as? PythonSdkAdditionalData)?.flavorAndData?.data is PyCondaFlavorData

internal fun TargetEnvironmentConfiguration?.toExecutor(): TargetCommandExecutor {
  return TargetEnvironmentRequestCommandExecutor(this?.createEnvironmentRequest(project = null) ?: LocalTargetEnvironmentRequest())
}