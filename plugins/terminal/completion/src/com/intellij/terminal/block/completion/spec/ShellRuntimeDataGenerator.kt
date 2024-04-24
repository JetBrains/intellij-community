package com.intellij.terminal.block.completion.spec

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
sealed interface ShellRuntimeDataGenerator<out T> {
  suspend fun generate(context: ShellRuntimeContext): T
}

@ApiStatus.Experimental
fun <T> ShellRuntimeDataGenerator(generate: suspend (ShellRuntimeContext) -> T): ShellRuntimeDataGenerator<T> {
  return ShellRuntimeDataGeneratorImpl(generate)
}

internal class ShellRuntimeDataGeneratorImpl<T>(private val func: suspend (ShellRuntimeContext) -> T) : ShellRuntimeDataGenerator<T> {
  override suspend fun generate(context: ShellRuntimeContext): T {
    return func(context)
  }
}