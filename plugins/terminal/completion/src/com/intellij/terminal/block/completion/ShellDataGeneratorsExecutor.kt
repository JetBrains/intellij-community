package com.intellij.terminal.block.completion

import com.intellij.terminal.block.completion.spec.ShellRuntimeContext
import com.intellij.terminal.block.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ShellDataGeneratorsExecutor {
  suspend fun <T> execute(context: ShellRuntimeContext, generator: ShellRuntimeDataGenerator<T>): T
}