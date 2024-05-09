package com.intellij.terminal.block.completion.spec

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ShellArgumentSpec {
  val displayName: @Nls String?
  val isOptional: Boolean
  val isVariadic: Boolean
  val optionsCanBreakVariadicArg: Boolean

  val generators: List<ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>>>
}