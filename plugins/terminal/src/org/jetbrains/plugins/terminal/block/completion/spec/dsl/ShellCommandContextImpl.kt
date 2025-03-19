// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import com.intellij.terminal.completion.spec.*
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.createCacheKey
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.emptyListGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellCommandSpecImpl

/**
 * @param [parentNames] used to build cache key/debug name of the subcommand/option/argument generators
 */
internal class ShellCommandContextImpl(
  names: List<String>,
  private val parentNames: List<String> = emptyList()
) : ShellSuggestionContextBase(names), ShellCommandContext {
  override var requiresSubcommand: Boolean = false
  override var parserOptions: ShellCommandParserOptions = ShellCommandParserOptions.DEFAULT

  private var subcommandSuppliers: MutableList<suspend (ShellRuntimeContext) -> List<ShellCommandSpec>> = mutableListOf()
  private var dynamicOptionSuppliers: MutableList<suspend (ShellRuntimeContext) -> List<ShellOptionSpec>> = mutableListOf()
  private var staticOptionSuppliers: MutableList<() -> List<ShellOptionSpec>> = mutableListOf()
  private val argumentSuppliers: MutableList<() -> ShellArgumentSpec> = mutableListOf()

  private val parentNamesWithSelf: List<String> = parentNames + names.first()

  override fun subcommands(content: suspend ShellChildCommandsContext.(ShellRuntimeContext) -> Unit) {
    val supplier: suspend (ShellRuntimeContext) -> List<ShellCommandSpec> = { shellContext ->
      val context = ShellChildCommandsContextImpl(parentNamesWithSelf)
      content.invoke(context, shellContext)
      context.build()
    }
    subcommandSuppliers.add(supplier)
  }

  override fun dynamicOptions(content: suspend ShellChildOptionsContext.(ShellRuntimeContext) -> Unit) {
    val supplier: suspend (ShellRuntimeContext) -> List<ShellOptionSpec> = { shellContext ->
      val context = ShellChildOptionsContextImpl(parentNamesWithSelf)
      content.invoke(context, shellContext)
      context.build()
    }
    dynamicOptionSuppliers.add(supplier)
  }

  override fun option(vararg names: String, content: ShellOptionContext.() -> Unit) {
    val supplier = {
      val context = ShellOptionContextImpl(names.asList(), parentNamesWithSelf)
      content.invoke(context)
      context.build()
    }
    staticOptionSuppliers.add(supplier)
  }

  override fun argument(content: ShellArgumentContext.() -> Unit) {
    val argNumber = argumentSuppliers.size + 1
    val supplier = {
      val context = ShellArgumentContextImpl(parentNamesWithSelf, argNumber)
      content.invoke(context)
      context.build()
    }
    argumentSuppliers.add(supplier)
  }

  fun build(): List<ShellCommandSpec> {
    return names.map { name ->
      ShellCommandSpecImpl(
        name = name,
        displayName = displayName,
        descriptionSupplier = descriptionSupplier,
        insertValue = insertValue,
        priority = priority,
        requiresSubcommand = requiresSubcommand,
        parserOptions = parserOptions,
        subcommandsGenerator = createSubcommandsGenerator(),
        dynamicOptionsSupplier = createSingleDynamicOptionsSupplier(),
        staticOptionsSupplier = { staticOptionSuppliers.flatMap { it() } },
        argumentsSupplier = { argumentSuppliers.map { it() } },
        parentNames = parentNames
      )
    }
  }

  private fun createSubcommandsGenerator(): ShellRuntimeDataGenerator<List<ShellCommandSpec>> {
    return if (subcommandSuppliers.isNotEmpty()) {
      val cacheKey = createCacheKey(parentNamesWithSelf, "subcommands")
      ShellRuntimeDataGenerator(cacheKey) { shellContext ->
        subcommandSuppliers.flatMap { it(shellContext) }
      }
    }
    else emptyListGenerator()
  }

  private fun createSingleDynamicOptionsSupplier(): (suspend (ShellRuntimeContext) -> List<ShellOptionSpec>)? {
    return if (dynamicOptionSuppliers.isNotEmpty()) {
      { shellContext -> dynamicOptionSuppliers.flatMap { it(shellContext) } }
    }
    else null
  }
}
