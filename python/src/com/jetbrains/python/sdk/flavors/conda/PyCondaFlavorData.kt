// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors.conda

import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.flavors.PyFlavorData
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal

data class PyCondaFlavorData(val env: PyCondaEnv) : PyFlavorData {
  override fun prepareTargetCommandLine(sdk: Sdk, targetCommandLineBuilder: TargetedCommandLineBuilder) {
    addCondaPythonToTargetCommandLine(targetCommandLineBuilder, env, sdk)
  }

}