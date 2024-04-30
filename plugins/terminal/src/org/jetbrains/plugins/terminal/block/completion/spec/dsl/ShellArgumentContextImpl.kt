// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import com.intellij.terminal.block.completion.spec.ShellArgumentSpec
import com.intellij.terminal.block.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.block.completion.spec.ShellRuntimeContext
import com.intellij.terminal.block.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellArgumentSpecImpl
import java.util.function.Supplier

internal class ShellArgumentContextImpl : ShellArgumentContext {
  override var displayName: Supplier<String>? = null
  override var isOptional: Boolean = false
  override var isVariadic: Boolean = false
  override var optionsCanBreakVariadicArg: Boolean = true
  override var isCommand: Boolean = false

  private val generators: MutableList<ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>>> = mutableListOf()

  override fun generator(content: suspend (ShellRuntimeContext) -> List<ShellCompletionSuggestion>) {
    generators.add(ShellRuntimeDataGenerator { content.invoke(it) })
  }

  fun build(): ShellArgumentSpec {
    return ShellArgumentSpecImpl(displayNameSupplier = displayName, isOptional = isOptional, isVariadic = isVariadic, optionsCanBreakVariadicArg = optionsCanBreakVariadicArg, isCommand = isCommand, generators = generators)
  }
}