// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run.target

import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.execution.target.value.constant
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class HelpersAwareLocalTargetEnvironmentRequest : HelpersAwareTargetEnvironmentRequest {
  override val targetEnvironmentRequest: TargetEnvironmentRequest = LocalTargetEnvironmentRequest()

  override fun preparePyCharmHelpers(): PythonHelpersMappings =
    PythonHelpersMappings(
      getPythonHelpers().map { it.mapToSelf() }
    )
}

private fun Path.mapToSelf(): PathMapping = PathMapping(localPath = this, targetPathFun = constant(absolutePathString()))
