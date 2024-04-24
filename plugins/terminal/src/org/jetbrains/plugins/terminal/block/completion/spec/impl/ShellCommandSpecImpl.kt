// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.terminal.block.completion.spec.*
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

internal class ShellCommandSpecImpl(
  names: List<String>,
  override val displayName: String?,
  descriptionSupplier: Supplier<@Nls String>?,
  override val insertValue: String?,
  override val priority: Int,
  override val requiresSubcommand: Boolean,
  override val parserDirectives: ShellCommandParserDirectives,
  override val subcommandsGenerator: ShellRuntimeDataGenerator<List<ShellCommandSpec>>,
  override val optionsGenerator: ShellRuntimeDataGenerator<List<ShellOptionSpec>>,
  override val argumentsGenerator: ShellRuntimeDataGenerator<List<ShellArgumentSpec>>
) : ShellCompletionSuggestionBase(names, descriptionSupplier), ShellCommandSpec {
  override fun toString(): String {
    return "ShellCommandSpecImpl(names=$names, displayName=$displayName, insertValue=$insertValue, priority=$priority, requiresSubcommand=$requiresSubcommand, parserDirectives=$parserDirectives, description=$description, subcommandsGenerator=$subcommandsGenerator, optionsGenerator=$optionsGenerator, argumentsGenerator=$argumentsGenerator)"
  }
}