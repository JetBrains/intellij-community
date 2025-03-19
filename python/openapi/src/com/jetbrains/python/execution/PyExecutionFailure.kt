// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.execution

import com.intellij.openapi.util.NlsContexts
import com.jetbrains.python.PyCommunityBundle
import org.jetbrains.annotations.Nls

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

/**
 *  User-readable message about this problem
 */
val PyExecutionFailure.userMessage: @Nls String get() = getUserMessage(command, args, additionalMessage, failureReason)

internal fun getUserMessage(
  command: String,
  args: List<String>,
  additionalMessage: @NlsContexts.DialogTitle String?,
  failureReason: FailureReason,
): @Nls String = when (val r = failureReason) {
  FailureReason.CantStart -> {
    PyCommunityBundle.message("python.execution.cant.start.error",
                              additionalMessage ?: "",
                              (listOf(command) + args).joinToString(" "))
  }
  is FailureReason.ExecutionFailed -> {

    PyCommunityBundle.message("python.execution.error",
                              additionalMessage ?: "",
                              (listOf(command) + args).joinToString(" "),
                              r.output.stdout,
                              r.output.stderr,
                              r.output.getExitCode())
  }
}
