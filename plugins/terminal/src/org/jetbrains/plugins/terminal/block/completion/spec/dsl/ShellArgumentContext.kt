// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import com.intellij.terminal.block.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.block.completion.spec.ShellRuntimeContext
import com.intellij.terminal.block.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

@ApiStatus.Experimental
@ShellCommandSpecDsl
sealed interface ShellArgumentContext {
  var displayName: Supplier<@Nls String>?
  var isOptional: Boolean
  var isVariadic: Boolean
  var optionsCanBreakVariadicArg: Boolean

  fun suggestions(content: suspend (ShellRuntimeContext) -> List<ShellCompletionSuggestion>)

  fun suggestions(generator: ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>>)

  fun suggestions(vararg names: String)
}
