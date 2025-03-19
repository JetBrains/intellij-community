// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.hatch

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.hatch.EnvironmentCreationHatchError
import com.intellij.python.hatch.PythonVirtualEnvironment
import com.intellij.python.hatch.getHatchEnvVirtualProjectPath
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.failure
import com.jetbrains.python.resolvePythonBinary
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.createSdk
import com.jetbrains.python.sdk.setAssociationToPath
import org.jetbrains.annotations.ApiStatus
import kotlin.io.path.name

@ApiStatus.Internal
suspend fun PythonVirtualEnvironment.Existing.createSdk(module: Module): Result<Sdk, PyError> {
  val pythonBinary = pythonHomePath.resolvePythonBinary()
                     ?: return failure("Cannot find Python Binary")

  val sdk = createSdk(
    sdkHomePath = pythonBinary,
    existingSdks = ProjectJdkTable.getInstance().allJdks.asList(),
    associatedProjectPath = module.project.basePath,
    suggestedSdkName = suggestHatchSdkName(),
    sdkAdditionalData = HatchSdkAdditionalData()
  ).getOrElse { exception ->
    return Result.failure(EnvironmentCreationHatchError(exception.localizedMessage))
  }.also {
    it.setAssociationToPath(module.basePath.toString())
  }
  return Result.success(sdk)
}

private fun PythonVirtualEnvironment.Existing.suggestHatchSdkName(): @NlsSafe String {
  val normalizedProjectName = pythonHomePath.getHatchEnvVirtualProjectPath().name
  val nonDefaultEnvName = pythonHomePath.name.takeIf { it != normalizedProjectName }

  val envNamePrefix = nonDefaultEnvName?.let { "$it@" } ?: ""
  val sdkName = "Hatch ($envNamePrefix$normalizedProjectName) [$pythonVersion]"
  return sdkName
}
