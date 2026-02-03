// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl.processLaunchers

import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.python.community.execService.spi.TargetEnvironmentRequestHandler
import java.nio.file.Path

class DefaultTargetEnvironmentRequestHandler : TargetEnvironmentRequestHandler {
  override fun mapUploadRoots(request: TargetEnvironmentRequest, localDirs: Set<Path>): List<TargetEnvironment.UploadRoot> {
    val result = localDirs.map { localDir ->
      TargetEnvironment.UploadRoot(
        localRootPath = localDir,
        targetRootPath = TargetEnvironment.TargetPath.Temporary(),
        removeAtShutdown = true
      )
    }
    return result
  }

  override fun isApplicable(request: TargetEnvironmentRequest): Boolean = true
}