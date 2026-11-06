// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors.conda

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.BinOnTarget
import com.intellij.python.community.execService.BinaryToExec
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Encapsulates conda binary command to simplify target request creation
 */
@ApiStatus.Internal

class PyCondaCommand(
  val fullCondaPathOnTarget: FullPathOnTarget,
  internal val targetConfig: TargetEnvironmentConfiguration?,
  internal val project: Project? = null,
  internal val indicator: TargetProgressIndicator = TargetProgressIndicator.EMPTY,
) {
  fun getCondaPath(): Path = Path(fullCondaPathOnTarget)

  fun asBinaryToExec(): BinaryToExec = when (targetConfig) {
    null -> BinOnEel(getCondaPath())
    else -> BinOnTarget(fullCondaPathOnTarget, targetConfig)
  }
}

