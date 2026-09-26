// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.completion.spec

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
sealed interface ProcessExecutionResult {
  /**
   * The process finished (possibly with a non-zero exit code).
   */
  data class Finished(val output: String, val exitCode: Int) : ProcessExecutionResult

  /**
   * The process failed to start or an error occurred during execution.
   */
  data class Failed(val exception: Exception) : ProcessExecutionResult
}