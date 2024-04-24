// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.json

import com.intellij.terminal.block.completion.spec.ShellArgumentSpec
import com.intellij.terminal.block.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.block.completion.spec.ShellRuntimeDataGenerator
import com.intellij.terminal.block.completion.spec.ShellSuggestionType
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCompletionSuggestion
import org.jetbrains.terminal.completion.ShellArgument
import java.util.function.Supplier

internal class ShellJsonBasedArgumentSpec(private val data: ShellArgument) : ShellArgumentSpec {
  override val displayName: String?
    get() = data.displayName

  override val isOptional: Boolean
    get() = data.isOptional

  override val isVariadic: Boolean
    get() = data.isVariadic

  override val optionsCanBreakVariadicArg: Boolean
    get() = data.optionsCanBreakVariadicArg

  override val isCommand: Boolean
    get() = data.isCommand

  override val generators: List<ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>>> by lazy {
    val generator = ShellRuntimeDataGenerator {
      // TODO: also add suggestions from templates (files)
      data.suggestions.flatMap { s ->
        val description = s.description?.let { Supplier { it } }
        s.names.map { name ->
          ShellCompletionSuggestion(name, ShellSuggestionType.ARGUMENT, s.displayName, description, s.insertValue, s.priority)
        }
      }
    }
    listOf(generator)
  }
}