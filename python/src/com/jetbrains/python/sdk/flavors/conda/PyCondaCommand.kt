// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors.conda

import com.intellij.execution.target.*
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.execution.target.readableFs.PathInfo
import com.intellij.execution.target.readableFs.TargetConfigurationReadableFs
import com.intellij.openapi.project.Project
import com.jetbrains.python.FullPathOnTarget
import com.jetbrains.python.sdk.flavors.conda.CondaPathFix.Companion.shouldBeFixed
import java.nio.file.Path

/**
 * Encapsulates conda binary command to simplify target request creation
 */
class PyCondaCommand(
  internal val fullCondaPathOnTarget: FullPathOnTarget,
  internal val targetConfig: TargetEnvironmentConfiguration?,
  internal val project: Project? = null,
  internal val indicator: TargetProgressIndicator = TargetProgressIndicator.EMPTY
) {
  private fun createRequest(): Result<TargetEnvironmentRequest> {
    (targetConfig as? TargetConfigurationReadableFs)?.let {
      val pathInfo = it.getPathInfo(fullCondaPathOnTarget)
      if (pathInfo == null) {
        return Result.failure(Exception("$fullCondaPathOnTarget does not exist"))
      }
      if ((pathInfo as? PathInfo.RegularFile)?.executable != true) {
        return Result.failure(Exception("$fullCondaPathOnTarget is not executable file"))
      }
    }
    return Result.success(targetConfig?.createEnvironmentRequest(project) ?: LocalTargetEnvironmentRequest())
  }

  fun createRequestEnvAndCommandLine(): Result<Triple<TargetEnvironmentRequest, TargetEnvironment, TargetedCommandLineBuilder>> {
    val request = createRequest().getOrElse { return Result.failure(it) }

    val env = request.prepareEnvironment(indicator)
    val commandLineBuilder = TargetedCommandLineBuilder(request).apply {
      setExePath(fullCondaPathOnTarget)
      if (shouldBeFixed) {
        CondaPathFix.ByCondaFullPath(Path.of(fullCondaPathOnTarget)).fix(this)
      }
    }
    return Result.success(Triple(request, env, commandLineBuilder))
  }
}