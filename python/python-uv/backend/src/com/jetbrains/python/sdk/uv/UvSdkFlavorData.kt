package com.jetbrains.python.sdk.uv

import com.intellij.execution.ExecutionException
import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.sdk.add.v2.toFileSystem
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.venvReader.VirtualEnvReader
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * TODO PY-91452 Should drop as a whole
 * uvWorkingDirectory - workingDirectory in PythonSdkAdditionalData
 * usePip - can be deduced based on requirementsFile in PythonSdkAdditionalData
 * venvPath - sdkHome
 * uvPath - stored as a setting for local EELs, for targets we can use detection only
 */
@ApiStatus.Internal
data class UvSdkFlavorData(
  val uvWorkingDirectory: Path?,
  val usePip: Boolean?,
  val venvPath: FullPathOnTarget?,
  val uvPath: FullPathOnTarget?,
) : PyFlavorData {


  @RequiresBackgroundThread
  @Throws(ExecutionException::class)
  override fun prepareTargetCommandLine(sdk: Sdk, targetCommandLineBuilder: TargetedCommandLineBuilder) {
    val fs = runBlockingMaybeCancellable { sdk.toFileSystem() }
    PyFlavorData.Utils.prepareTargetCommandLine(sdk, targetCommandLineBuilder) { interpreterPath ->
      val envPath =
        VirtualEnvReader().resolvePythonHomeFromBinaryOrDir(interpreterPath, targetCommandLineBuilder.request.targetPlatform.platform)
      fs.parsePath(envPath)
        .mapSuccess { targetCommandLineBuilder.addEnvironmentVariable("UV_PROJECT_ENVIRONMENT", it.toStringOnRemoteMachine()) }
    }
  }
}