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
  private var optionsSupplier: () -> List<ShellOptionSpec> = { emptyList() }
  private val argumentSuppliers: MutableList<() -> ShellArgumentSpec> = mutableListOf()

  private val parentNamesWithSelf: List<String> = parentNames + names.first()

  override fun subcommands(content: suspend ShellChildCommandsContext.(ShellRuntimeContext) -> Unit) {
    val cacheKey = createCacheKey(parentNamesWithSelf, "subcommands")
    subcommandsGenerator = ShellRuntimeDataGenerator(cacheKey) { shellContext ->
      val context = ShellChildCommandsContextImpl(parentNamesWithSelf)
      content.invoke(context, shellContext)
      context.build()
    }
  }

  override fun options(content: ShellChildOptionsContext.() -> Unit) {
    optionsSupplier = {
      val context = ShellChildOptionsContextImpl(parentNamesWithSelf)
      content.invoke(context)
      context.build()
    }
  }

  override fun argument(content: ShellArgumentContext.() -> Unit) {
    val supplier = {
      val context = ShellArgumentContextImpl(parentNamesWithSelf)
      content.invoke(context)
      context.build()
    }
    argumentSuppliers.add(supplier)
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
      optionsSupplier = optionsSupplier,
      argumentsSupplier = { argumentSuppliers.map { it() } }
    )
  }
}