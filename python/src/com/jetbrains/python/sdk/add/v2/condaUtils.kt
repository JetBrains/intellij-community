// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.sdk.add.target.conda.TargetCommandExecutor
import com.jetbrains.python.sdk.add.target.conda.TargetEnvironmentRequestCommandExecutor
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand

internal val PythonAddInterpreterPresenter.condaExecutableOnTarget: String
  @RequiresEdt get() = state.condaExecutable.get().convertToPathOnTarget(targetEnvironmentConfiguration)

@RequiresEdt
internal fun PythonAddInterpreterPresenter.createCondaCommand(): PyCondaCommand =
  PyCondaCommand(condaExecutableOnTarget, targetConfig = targetEnvironmentConfiguration)

@RequiresEdt
internal fun PythonAddInterpreterPresenter.createExecutor(): TargetCommandExecutor = targetEnvironmentConfiguration.toExecutor()

internal fun TargetEnvironmentConfiguration?.toExecutor(): TargetCommandExecutor {
  return TargetEnvironmentRequestCommandExecutor(this?.createEnvironmentRequest(project = null) ?: LocalTargetEnvironmentRequest())
}