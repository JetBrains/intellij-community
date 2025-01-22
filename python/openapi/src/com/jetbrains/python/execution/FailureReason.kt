// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.execution

import com.intellij.execution.process.ProcessOutput
import com.jetbrains.python.packaging.PyExecutionException

/**
 * Types of execution error for [PyExecutionFailure]
 */
sealed interface FailureReason {
  /**
   * A process failed to start, or the code that ought to start it decided not to run it.
   * That means the process hasn't been even created.
   */
  data object CantStart : FailureReason

  /**
   * A process started but failed with an error. See [output] for the result
   */
  data class ExecutionFailed(val output: ProcessOutput) : FailureReason
}

internal fun copyWith(ex: PyExecutionException, newCommand: String, newArgs: List<String>): PyExecutionException =
  when (val err = ex.failureReason) {
    FailureReason.CantStart -> {
      PyExecutionException(ex.additionalMessage, newCommand, newArgs, ex.fixes)
    }
    is FailureReason.ExecutionFailed -> {
      PyExecutionException(ex.additionalMessage, newCommand, newArgs, err.output, ex.fixes)
    }
  }