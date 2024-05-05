// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import com.intellij.terminal.block.completion.spec.*
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.createCacheKey
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.emptyListGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellCommandSpecImpl

/**
 * @param [parentNames] used to build cache key/debug name of the subcommand/option/argument generators
 */
internal class ShellCommandContextImpl(
  names: List<String>,
  parentNames: List<String> = emptyList()
) : ShellSuggestionContextBase(names), ShellCommandContext {
  override var requiresSubcommand: Boolean = false
  override var parserDirectives: ShellCommandParserDirectives = ShellCommandParserDirectives.DEFAULT

  private var subcommandsGenerator: ShellRuntimeDataGenerator<List<ShellCommandSpec>>? = null
  private var optionsGenerator: ShellRuntimeDataGenerator<List<ShellOptionSpec>>? = null
  private var argumentsGenerator: ShellRuntimeDataGenerator<List<ShellArgumentSpec>>? = null

  private val parentNamesWithSelf: List<String> = parentNames + names.first()

  override fun subcommands(content: suspend ShellChildCommandsContext.(ShellRuntimeContext) -> Unit) {
    val cacheKey = createCacheKey(parentNamesWithSelf, "subcommands")
    subcommandsGenerator = ShellRuntimeDataGenerator(cacheKey) { shellContext ->
      val context = ShellChildCommandsContextImpl(parentNamesWithSelf)
      content.invoke(context, shellContext)
      context.build()
    }
  }

  override fun options(content: suspend ShellChildOptionsContext.(ShellRuntimeContext) -> Unit) {
    val cacheKey = createCacheKey(parentNamesWithSelf, "options")
    optionsGenerator = ShellRuntimeDataGenerator(cacheKey) { shellContext ->
      val context = ShellChildOptionsContextImpl(parentNamesWithSelf)
      content.invoke(context, shellContext)
      context.build()
    }
  }

  override fun arguments(content: suspend ShellChildArgumentsContext.(ShellRuntimeContext) -> Unit) {
    val cacheKey = createCacheKey(parentNamesWithSelf, "arguments")
    argumentsGenerator = ShellRuntimeDataGenerator(cacheKey) { shellContext ->
      val context = ShellChildArgumentsContextImpl(parentNamesWithSelf)
      content.invoke(context, shellContext)
      context.build()
    }
  }

  fun build(): ShellCommandSpec {
    return ShellCommandSpecImpl(
      names = names,
      displayName = displayName,
      descriptionSupplier = description,
      insertValue = insertValue,
      priority = priority,
      requiresSubcommand = requiresSubcommand,
      parserDirectives = parserDirectives,
      subcommandsGenerator = subcommandsGenerator ?: emptyListGenerator(),
      optionsGenerator = optionsGenerator ?: emptyListGenerator(),
      argumentsGenerator = argumentsGenerator ?: emptyListGenerator()
    )
  }
}