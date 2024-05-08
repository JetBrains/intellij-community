// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import com.intellij.terminal.block.completion.spec.*
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCompletionSuggestion
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.createCacheKey
import org.jetbrains.plugins.terminal.block.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellArgumentSpecImpl
import java.util.function.Supplier

/**
 * @param [parentCommandNames] used to build cache key/debug name of the generators
 */
internal class ShellArgumentContextImpl(private val parentCommandNames: List<String>) : ShellArgumentContext {
  override var displayName: Supplier<String>? = null
  override var isOptional: Boolean = false
  override var isVariadic: Boolean = false
  override var optionsCanBreakVariadicArg: Boolean = true
  override var isCommand: Boolean = false

  private val generators: MutableList<ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>>> = mutableListOf()

  override fun generator(content: suspend (ShellRuntimeContext) -> List<ShellCompletionSuggestion>) {
    val cacheKey = createCacheKey(parentCommandNames, "arg ${generators.count() + 1}")
    generators.add(ShellRuntimeDataGenerator(cacheKey) { content.invoke(it) })
  }

  override fun generator(generator: ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>>) {
    generators.add(generator)
  }

  override fun suggestions(vararg names: String) {
    val debugName = createCacheKey(parentCommandNames, "arg ${generators.count() + 1}")
    val generator = ShellRuntimeDataGenerator(debugName = debugName) {
      names.map { ShellCompletionSuggestion(it, type = ShellSuggestionType.ARGUMENT) }
    }
    generators.add(generator)
  }

  fun build(): ShellArgumentSpec {
    return ShellArgumentSpecImpl(displayNameSupplier = displayName, isOptional = isOptional, isVariadic = isVariadic, optionsCanBreakVariadicArg = optionsCanBreakVariadicArg, isCommand = isCommand, generators = generators)
  }
}