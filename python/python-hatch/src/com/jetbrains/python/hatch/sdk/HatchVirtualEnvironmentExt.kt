// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.hatch.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.hatch.BasePythonExecutableNotFoundHatchError
import com.intellij.python.hatch.HatchVirtualEnvironment
import com.intellij.python.hatch.PythonVirtualEnvironment
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.target.ui.TargetPanelExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Builds a hatch-typed SDK for this environment, failing when it has no usable base interpreter.
 *
 * Lives in the hatch module beside [HatchSdkAdditionalData], the data it writes: everything it touches is either hatch's
 * own env model or a `FileSystem` member ([FileSystem.setupSdk], [FileSystem.resolvePythonBinary]) from `python-sdk`, so
 * nothing here needs the monolith above. That is what lets the widget's hatch provider — which lives in this module —
 * create an SDK itself instead of handing the job back to a central switch.
 *
 * Public rather than `internal` because the v2 Add dialog and the SDK-configuration extension, both above this module,
 * call it too.
 */
@ApiStatus.Internal
suspend fun <P : PathHolder> HatchVirtualEnvironment<P>.createSdk(
  workingDirectoryPath: Path,
  fileSystem: FileSystem<P>,
  targetPanelExtension: TargetPanelExtension? = null,
): PyResult<Sdk> {
  val existingVirtualEnvironment = when (val virtualEnvironment = pythonVirtualEnvironment) {
    is PythonVirtualEnvironment.Existing -> virtualEnvironment
    is PythonVirtualEnvironment.NotExisting -> {
      return Result.failure(BasePythonExecutableNotFoundHatchError(virtualEnvironment.pythonHomePath.toString()))
    }
    null -> return Result.failure(BasePythonExecutableNotFoundHatchError(pathString = null))
  }
  val pythonHomePath = existingVirtualEnvironment.pythonHomePath
  val pythonBinary = withContext(Dispatchers.IO) { fileSystem.resolvePythonBinary(pythonHomePath) }
                     ?: return Result.failure(BasePythonExecutableNotFoundHatchError(pythonHomePath.toString()))

  val hatchSdkAdditionalData = HatchSdkAdditionalData(
    hatchWorkingDirectory = workingDirectoryPath,
    hatchEnvironmentName = this.hatchEnvironment.name,
  )
  val sdk = fileSystem.setupSdk(
    project = null,
    pythonBinaryPath = pythonBinary,
    sdkAdditionalData = hatchSdkAdditionalData,
    targetPanelExtension = targetPanelExtension,
    suggestedSdkName = null,
  ).getOr { return it }


  return Result.success(sdk)
}
