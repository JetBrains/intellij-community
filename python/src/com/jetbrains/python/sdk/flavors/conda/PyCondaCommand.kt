// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors.conda

import com.intellij.execution.target.*
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.pathValidation.PlatformAndRoot.Companion.getPlatformAndRoot
import com.jetbrains.python.pathValidation.ValidationRequest
import com.jetbrains.python.pathValidation.validateExecutableFile
import org.jetbrains.annotations.ApiStatus

/**
 * Encapsulates conda binary command to simplify target request creation
 */
@ApiStatus.Internal

class PyCondaCommand(
  val fullCondaPathOnTarget: FullPathOnTarget,
  internal val targetConfig: TargetEnvironmentConfiguration?,
  internal val project: Project? = null,
  internal val indicator: TargetProgressIndicator = TargetProgressIndicator.EMPTY
) {

  @RequiresBackgroundThread
  private fun createRequest(): Result<TargetEnvironmentRequest> {
    validateExecutableFile(ValidationRequest(fullCondaPathOnTarget, platformAndRoot = targetConfig.getPlatformAndRoot()))?.let {
      return Result.failure(Exception(it.message))
    }
    return Result.success(targetConfig?.createEnvironmentRequest(project) ?: LocalTargetEnvironmentRequest())
  }

  @RequiresBackgroundThread
  fun createRequestEnvAndCommandLine(): Result<Triple<TargetEnvironmentRequest, TargetEnvironment, TargetedCommandLineBuilder>> {
    val request = createRequest().getOrElse { return Result.failure(it) }

    val env = request.prepareEnvironment(indicator)
    val commandLineBuilder = TargetedCommandLineBuilder(request).apply {
      setExePath(fullCondaPathOnTarget)
      fixCondaPathEnvIfNeeded(fullCondaPathOnTarget)
    }
    return Result.success(Triple(request, env, commandLineBuilder))
  }
}