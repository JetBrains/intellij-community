// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.hatch.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.hatch.BasePythonExecutableNotFoundHatchError
import com.intellij.python.hatch.HatchVirtualEnvironment
import com.intellij.python.hatch.PythonVirtualEnvironment
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.createSdk
import com.jetbrains.python.sdk.impl.resolvePythonBinary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
internal suspend fun HatchVirtualEnvironment.createSdk(workingDirectoryPath: Path): PyResult<Sdk> {
  if (pythonVirtualEnvironment !is PythonVirtualEnvironment.Existing) {
    return Result.failure(BasePythonExecutableNotFoundHatchError(null as String?))
  }
  val pythonHomePath = pythonVirtualEnvironment?.pythonHomePath
  val pythonBinary = pythonHomePath?.let {
    withContext(Dispatchers.IO) { it.resolvePythonBinary() }
  } ?: return Result.failure(BasePythonExecutableNotFoundHatchError(pythonHomePath))

  val hatchSdkAdditionalData = HatchSdkAdditionalData(workingDirectoryPath, this.hatchEnvironment.name)
  val sdk = createSdk(
    pythonBinaryPath = PathHolder.Eel(pythonBinary),
    sdkAdditionalData = hatchSdkAdditionalData
  ).getOr { return it }


  return Result.success(sdk)
}
