// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import com.intellij.terminal.block.completion.spec.*
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellCommandSpecImpl

internal class ShellCommandContextImpl(names: List<String>) : ShellSuggestionContextBase(names), ShellCommandContext {
  override var requiresSubcommand: Boolean = false
  override var parserDirectives: ShellCommandParserDirectives = ShellCommandParserDirectives.DEFAULT

  private var subcommandsGenerator: ShellRuntimeDataGenerator<List<ShellCommandSpec>>? = null
  private var optionsGenerator: ShellRuntimeDataGenerator<List<ShellOptionSpec>>? = null
  private var argumentsGenerator: ShellRuntimeDataGenerator<List<ShellArgumentSpec>>? = null

  override fun subcommands(content: suspend ShellChildCommandsContext.(ShellRuntimeContext) -> Unit) {
    subcommandsGenerator = ShellRuntimeDataGenerator { shellContext ->
      val context = ShellChildCommandsContextImpl()
      content.invoke(context, shellContext)
      context.build()
    }
  }

  override fun options(content: suspend ShellChildOptionsContext.(ShellRuntimeContext) -> Unit) {
    optionsGenerator = ShellRuntimeDataGenerator { shellContext ->
      val context = ShellChildOptionsContextImpl()
      content.invoke(context, shellContext)
      context.build()
    }
  }

  override fun arguments(content: suspend ShellChildArgumentsContext.(ShellRuntimeContext) -> Unit) {
    argumentsGenerator = ShellRuntimeDataGenerator { shellContext ->
      val context = ShellChildArgumentsContextImpl()
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

  private fun <T> emptyListGenerator(): ShellRuntimeDataGenerator<List<T>> {
    return ShellRuntimeDataGenerator { emptyList() }
  }
}