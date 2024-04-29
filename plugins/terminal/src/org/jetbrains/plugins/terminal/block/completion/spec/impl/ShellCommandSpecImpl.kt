// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.terminal.block.completion.spec.*
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.emptyListGenerator
import java.util.function.Supplier

internal class ShellCommandSpecImpl(
  names: List<String>,
  override val displayName: String? = null,
  descriptionSupplier: Supplier<@Nls String>? = null,
  override val insertValue: String? = null,
  override val priority: Int = 50,
  override val requiresSubcommand: Boolean = false,
  override val parserDirectives: ShellCommandParserDirectives = ShellCommandParserDirectives.DEFAULT,
  override val subcommandsGenerator: ShellRuntimeDataGenerator<List<ShellCommandSpec>> = emptyListGenerator(),
  override val optionsGenerator: ShellRuntimeDataGenerator<List<ShellOptionSpec>> = emptyListGenerator(),
  override val argumentsGenerator: ShellRuntimeDataGenerator<List<ShellArgumentSpec>> = emptyListGenerator()
) : ShellCompletionSuggestionBase(names, descriptionSupplier), ShellCommandSpec {
  override fun toString(): String {
    return "ShellCommandSpecImpl(names=$names, displayName=$displayName, insertValue=$insertValue, priority=$priority, requiresSubcommand=$requiresSubcommand, parserDirectives=$parserDirectives, description=$description, subcommandsGenerator=$subcommandsGenerator, optionsGenerator=$optionsGenerator, argumentsGenerator=$argumentsGenerator)"
  }
}