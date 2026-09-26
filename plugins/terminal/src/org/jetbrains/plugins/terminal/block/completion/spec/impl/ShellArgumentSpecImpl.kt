// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.terminal.completion.spec.ShellArgumentSpec
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

internal class ShellArgumentSpecImpl(
  private val displayNameSupplier: Supplier<@Nls String>?,
  override val isOptional: Boolean,
  override val isVariadic: Boolean,
  override val optionsCanBreakVariadicArg: Boolean,
  override val generators: List<ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>>>
) : ShellArgumentSpec {
  override val displayName: String?
    get() = displayNameSupplier?.get()

  override fun toString(): String {
    return "ShellArgumentSpecImpl(displayName: $displayName, isOptional=$isOptional, isVariadic=$isVariadic, optionsCanBreakVariadicArg=$optionsCanBreakVariadicArg, generators=$generators)"
  }
}