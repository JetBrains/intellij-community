package com.intellij.terminal.block.completion.spec

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ShellRuntimeDataGenerator<out T> {
  suspend fun generate(context: ShellRuntimeContext): T
}