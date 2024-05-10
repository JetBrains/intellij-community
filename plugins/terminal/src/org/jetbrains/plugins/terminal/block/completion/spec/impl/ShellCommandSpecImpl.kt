// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.terminal.completion.spec.*
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.emptyListGenerator
import java.util.function.Supplier
import javax.swing.Icon

internal class ShellCommandSpecImpl(
  names: List<String>,
  override val displayName: String? = null,
  descriptionSupplier: Supplier<@Nls String>? = null,
  override val insertValue: String? = null,
  override val priority: Int = 50,
  override val requiresSubcommand: Boolean = false,
  override val parserOptions: ShellCommandParserOptions = ShellCommandParserOptions.DEFAULT,
  override val subcommandsGenerator: ShellRuntimeDataGenerator<List<ShellCommandSpec>> = emptyListGenerator(),
  private val optionsSupplier: () -> List<ShellOptionSpec> = { emptyList() },
  private val argumentsSupplier: () -> List<ShellArgumentSpec> = { emptyList() }
) : ShellCompletionSuggestionBase(names, descriptionSupplier), ShellCommandSpec {
  override val options: List<ShellOptionSpec> by lazy { optionsSupplier() }
  override val arguments: List<ShellArgumentSpec> by lazy { argumentsSupplier() }

  // the icon of command will be specified in the completion logic
  override val icon: Icon? = null

  init {
    if (priority !in 0..100) {
      error("Priority must be between 0 and 100")
    }
  }

  override fun toString(): String {
    return "ShellCommandSpecImpl(names=$names, displayName=$displayName, insertValue=$insertValue, priority=$priority, requiresSubcommand=$requiresSubcommand, parserOptions=$parserOptions, description=$description, subcommandsGenerator=$subcommandsGenerator, options=$options, arguments=$arguments)"
  }
}