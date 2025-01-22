// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.execution

import com.intellij.openapi.util.NlsContexts

/**
 * Some command can't be executed
 */
interface PyExecutionFailure {
  val command: String

  val args: List<String>

  /**
   * optional message to be displayed to the user
   */
  val additionalMessage: @NlsContexts.DialogTitle String?


  val failureReason: FailureReason
}
