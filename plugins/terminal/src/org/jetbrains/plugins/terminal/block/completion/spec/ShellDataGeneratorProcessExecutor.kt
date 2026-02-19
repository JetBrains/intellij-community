// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec

import com.intellij.terminal.completion.spec.ShellCommandResult
import org.jetbrains.annotations.ApiStatus

/**
 * This executor is used in the implementation of [com.intellij.terminal.completion.spec.ShellRuntimeContext]
 * to support execution of the processes in the OS.
 */
@ApiStatus.Experimental
interface ShellDataGeneratorProcessExecutor {
  suspend fun executeProcess(options: ShellDataGeneratorProcessOptions): ShellCommandResult
}

@ApiStatus.Experimental
sealed interface ShellDataGeneratorProcessOptions {
  val executable: String
  val args: List<String>
  val workingDirectory: String
  val env: Map<String, String>
}

@ApiStatus.Internal
data class ShellDataGeneratorProcessOptionsImpl(
  override val executable: String,
  override val args: List<String>,
  override val workingDirectory: String,
  override val env: Map<String, String>,
) : ShellDataGeneratorProcessOptions