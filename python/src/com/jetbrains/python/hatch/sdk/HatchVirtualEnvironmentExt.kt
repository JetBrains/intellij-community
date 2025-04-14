// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.hatch.sdk

import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.hatch.*
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.resolvePythonBinary
import com.jetbrains.python.sdk.createSdk
import com.jetbrains.python.sdk.persist
import com.jetbrains.python.sdk.setAssociationToModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.name

@ApiStatus.Internal
suspend fun HatchVirtualEnvironment.createSdk(workingDirectoryPath: Path, module: Module?): Result<Sdk, PyError> {
  val existingPythonEnvironment = pythonVirtualEnvironment as? PythonVirtualEnvironment.Existing
                                  ?: return Result.failure(BasePythonExecutableNotFoundHatchError(null as String?))
  val pythonHomePath = pythonVirtualEnvironment?.pythonHomePath
  val pythonBinary = pythonHomePath?.let {
    withContext(Dispatchers.IO) { it.resolvePythonBinary() }
  } ?: return Result.failure(BasePythonExecutableNotFoundHatchError(pythonHomePath))

  val hatchSdkAdditionalData = HatchSdkAdditionalData(workingDirectoryPath, this.hatchEnvironment.name)
  val sdk = createSdk(
    sdkHomePath = pythonBinary,
    existingSdks = ProjectJdkTable.getInstance().allJdks.asList(),
    associatedProjectPath = module?.project?.basePath,
    suggestedSdkName = existingPythonEnvironment.suggestHatchSdkName(),
    sdkAdditionalData = hatchSdkAdditionalData
  ).getOrElse { exception ->
    return Result.failure(EnvironmentCreationHatchError(exception.localizedMessage))
  }

  withContext(Dispatchers.EDT) {
    sdk.persist()
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
