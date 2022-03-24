// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run.target

import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.value.TargetEnvironmentFunction

/**
 * The target request for Python interpreter configured in PyCharm on a
 * specific target.
 */
interface HelpersAwareTargetEnvironmentRequest {
  val targetEnvironmentRequest: TargetEnvironmentRequest

  /**
   * The value that could be resolved to the path to the root of PyCharm
   * helpers scripts.
   */
  fun preparePyCharmHelpers(): TargetEnvironmentFunction<String>
}