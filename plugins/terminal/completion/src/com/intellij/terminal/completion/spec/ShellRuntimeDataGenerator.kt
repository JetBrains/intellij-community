package com.intellij.terminal.completion.spec

import org.jetbrains.annotations.ApiStatus

/**
 * Interface for providing some data with the help of [ShellRuntimeContext] in a suspending way.
 *
 * **Please do not implement this interface on your own**,
 * use [helper function][org.jetbrains.plugins.terminal.block.completion.spec.ShellRuntimeDataGenerator] instead.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ShellRuntimeDataGenerator<out T : Any> {
  suspend fun generate(context: ShellRuntimeContext): T
}