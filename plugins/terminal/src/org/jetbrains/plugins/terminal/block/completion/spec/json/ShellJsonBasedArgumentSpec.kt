// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.json

import com.intellij.terminal.completion.spec.ShellArgumentSpec
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.completion.spec.ShellRuntimeDataGenerator
import com.intellij.terminal.completion.spec.ShellSuggestionType
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCompletionSuggestion
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.availableCommandsGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.createCacheKey
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.fileSuggestionsGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.terminal.completion.ShellArgument

/**
 * @param [parentCommandNames] used to build cache key/debug name of the generators
 */
internal class ShellJsonBasedArgumentSpec(
  private val data: ShellArgument,
  private val parentCommandNames: List<String>
) : ShellArgumentSpec {
  override val displayName: String?
    get() = data.displayName

  override val isOptional: Boolean
    get() = data.isOptional

  override val isVariadic: Boolean
    get() = data.isVariadic

  override val optionsCanBreakVariadicArg: Boolean
    get() = data.optionsCanBreakVariadicArg

  override val generators: List<ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>>> by lazy {
    buildList {
      if (data.suggestions.isNotEmpty()) {
        add(ShellRuntimeDataGenerator(createCacheKey(parentCommandNames, "suggestions")) {
          data.suggestions.flatMap { s ->
            s.names.map { name ->
              // TODO: there should be a way to localize the json-based descriptions
              ShellCompletionSuggestion(name) {
                type(ShellSuggestionType.ARGUMENT)
                s.displayName?.let { displayName(it) }
                s.description?.let { description(it) }
                s.insertValue?.let { insertValue(it) }
                priority(s.priority)
              }
            }
          }
        })
      }
      if (data.isCommand) {
        add(availableCommandsGenerator())
      }
      if (data.isFilePath() || data.isFolder()) {
        add(fileSuggestionsGenerator(onlyDirectories = !data.isFilePath()))
      }
    }
  }

  fun ShellArgument.isFilePath(): Boolean = isWithTemplate("filepaths")

  fun ShellArgument.isFolder(): Boolean = isWithTemplate("folders")

  private fun ShellArgument.isWithTemplate(template: String): Boolean {
    return templates.contains(template) || generators.any { it.templates.contains(template) }
  }

  override fun toString(): String {
    return "ShellJsonBasedArgumentSpec(parentCommandNames=$parentCommandNames, data=$data)"
  }
}
