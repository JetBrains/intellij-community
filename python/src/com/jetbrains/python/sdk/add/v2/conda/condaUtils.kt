// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.conda

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.application.EDT
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.onSuccess
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.add.v2.PyProjectCreateHelpers
import com.jetbrains.python.sdk.add.v2.PythonAddInterpreterModel
import com.jetbrains.python.sdk.add.v2.convertToPathOnTarget
import com.jetbrains.python.sdk.add.v2.existingSdks
import com.jetbrains.python.sdk.conda.TargetCommandExecutor
import com.jetbrains.python.sdk.conda.TargetEnvironmentRequestCommandExecutor
import com.jetbrains.python.sdk.conda.createCondaSdkAlongWithNewEnv
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.persist
import com.jetbrains.python.sdk.setAssociationToModule
import kotlinx.coroutines.Dispatchers

@RequiresEdt
internal fun PythonAddInterpreterModel.createCondaCommand(): PyCondaCommand =
  PyCondaCommand(state.condaExecutable.get().convertToPathOnTarget(targetEnvironmentConfiguration),
                 targetConfig = targetEnvironmentConfiguration)

internal suspend fun PythonAddInterpreterModel.createCondaEnvironment(moduleOrProject: ModuleOrProject, request: NewCondaEnvRequest): PyResult<Sdk> {

  val result = withModalProgress(ModalTaskOwner.guess(),
                                 PyBundle.message("sdk.create.custom.conda.create.progress"),
                                 TaskCancellation.nonCancellable()) {
    createCondaCommand().createCondaSdkAlongWithNewEnv(
      newCondaEnvInfo = request,
      uiContext = Dispatchers.EDT,
      existingSdks = existingSdks,
      project = moduleOrProject.project
    )
  }.onSuccess { sdk ->
    (sdk.sdkType as PythonSdkType).setupSdkPaths(sdk)

    val module = PyProjectCreateHelpers.getModule(moduleOrProject, null)
    if (module != null) {
      sdk.setAssociationToModule(module)
    }
    sdk.persist()
  }

  return result
}

internal fun TargetEnvironmentConfiguration?.toExecutor(): TargetCommandExecutor {
  return TargetEnvironmentRequestCommandExecutor(this?.createEnvironmentRequest(project = null) ?: LocalTargetEnvironmentRequest())
}