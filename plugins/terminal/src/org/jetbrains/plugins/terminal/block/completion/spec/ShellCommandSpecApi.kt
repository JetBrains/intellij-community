// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec

import com.intellij.terminal.block.completion.spec.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.block.completion.spec.dsl.ShellCommandContext
import org.jetbrains.plugins.terminal.block.completion.spec.dsl.ShellCommandContextImpl
import org.jetbrains.plugins.terminal.block.completion.spec.dsl.ShellCommandSpecDsl
import org.jetbrains.plugins.terminal.block.completion.spec.impl.IJShellRuntimeDataGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellCompletionSuggestionImpl
import java.util.function.Supplier

@ApiStatus.Experimental
@ShellCommandSpecDsl
fun ShellCommandSpec(name: String, content: ShellCommandContext.() -> Unit): ShellCommandSpec {
  val context = ShellCommandContextImpl(listOf(name))
  content.invoke(context)
  return context.build()
}

@ApiStatus.Experimental
fun ShellCompletionSuggestion(
  name: String,
  type: ShellSuggestionType = ShellSuggestionType.ARGUMENT,
  displayName: String? = null,
  description: Supplier<@Nls String>? = null,
  insertValue: String? = null,
  priority: Int = 50
): ShellCompletionSuggestion {
  return ShellCompletionSuggestionImpl(listOf(name), type, displayName, description, insertValue, priority)
}

@ApiStatus.Experimental
fun <T> ShellRuntimeDataGenerator(
  cacheKeyAndDebugName: String? = null,
  generate: suspend (ShellRuntimeContext) -> T
): ShellRuntimeDataGenerator<T> {
  return IJShellRuntimeDataGenerator(cacheKeyAndDebugName, { cacheKeyAndDebugName }, generate)
}

@ApiStatus.Experimental
fun <T> ShellRuntimeDataGenerator(
  debugName: String? = null,
  getCacheKey: (ShellRuntimeContext) -> String? = { null },
  generate: suspend (ShellRuntimeContext) -> T
): ShellRuntimeDataGenerator<T> {
  return IJShellRuntimeDataGenerator(debugName, getCacheKey, generate)
}