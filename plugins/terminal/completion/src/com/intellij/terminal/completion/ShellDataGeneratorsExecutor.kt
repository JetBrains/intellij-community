package com.intellij.terminal.completion

import com.intellij.terminal.completion.spec.ShellRuntimeContext
import com.intellij.terminal.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ShellDataGeneratorsExecutor {
  suspend fun <T : Any> execute(context: ShellRuntimeContext, generator: ShellRuntimeDataGenerator<T>): T
}