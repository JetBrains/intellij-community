// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import com.intellij.terminal.completion.spec.ShellArgumentSpec
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import com.intellij.terminal.completion.spec.ShellRuntimeDataGenerator
import com.intellij.terminal.completion.spec.ShellSuggestionType
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCompletionSuggestion
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators
import org.jetbrains.plugins.terminal.block.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellArgumentSpecImpl
import java.util.function.Supplier

/**
 * Params [parentNames] and [argNumber] used to build cache key/debug name of the generators
 */
@Suppress("OVERRIDE_DEPRECATION")
internal class ShellArgumentContextImpl(
  private val parentNames: List<String>,
  private val argNumber: Int
) : ShellArgumentContext {
  override var isOptional: Boolean = false
  override var isVariadic: Boolean = false
  private var optionsCanBreakVariadicArg: Boolean = true

  private var displayNameSupplier: Supplier<String>? = null

  override fun displayName(text: String) {
    displayNameSupplier = Supplier { text }
  }

  override fun displayName(supplier: Supplier<String>) {
    displayNameSupplier = supplier
  }

  override fun optional() {
    isOptional = true
  }

  override fun variadic() {
    isVariadic = true
  }

  override fun optionsCantBreakVariadicArg() {
    optionsCanBreakVariadicArg = false
  }

  private val generators: MutableList<ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>>> = mutableListOf()

  override fun suggestions(content: suspend (ShellRuntimeContext) -> List<ShellCompletionSuggestion>) {
    val cacheKey = createCacheKey()
    generators.add(ShellRuntimeDataGenerator(cacheKey) { content.invoke(it) })
  }

  override fun suggestions(generator: ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>>) {
    generators.add(generator)
  }

  override fun suggestions(vararg names: String) {
    val generator = ShellRuntimeDataGenerator(debugName = createCacheKey()) {
      names.map { ShellCompletionSuggestion(it) { type(ShellSuggestionType.ARGUMENT) } }
    }
    generators.add(generator)
  }

  private fun createCacheKey(): String {
    return ShellDataGenerators.createCacheKey(parentNames, "arg$argNumber generator${generators.size + 1}")
  }

  fun build(): ShellArgumentSpec {
    return ShellArgumentSpecImpl(displayNameSupplier = displayNameSupplier, isOptional = isOptional, isVariadic = isVariadic, optionsCanBreakVariadicArg = optionsCanBreakVariadicArg, generators = generators)
  }
}