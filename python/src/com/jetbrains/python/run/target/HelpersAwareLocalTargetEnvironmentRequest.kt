// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run.target

import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.execution.target.value.TargetEnvironmentFunction
import com.intellij.execution.target.value.constant
import com.jetbrains.python.PythonHelpersLocator

class HelpersAwareLocalTargetEnvironmentRequest : HelpersAwareTargetEnvironmentRequest {
  override val targetEnvironmentRequest: TargetEnvironmentRequest = LocalTargetEnvironmentRequest()

  override fun preparePyCharmHelpers(): TargetEnvironmentFunction<String> = constant(PythonHelpersLocator.getHelpersRoot().path)
}