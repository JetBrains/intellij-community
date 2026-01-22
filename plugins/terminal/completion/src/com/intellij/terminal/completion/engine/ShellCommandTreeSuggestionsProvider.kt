// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.completion.engine

import com.intellij.terminal.completion.ShellArgumentSuggestion
import com.intellij.terminal.completion.ShellDataGeneratorsExecutor
import com.intellij.terminal.completion.spec.*

internal class ShellCommandTreeSuggestionsProvider(
  private val context: ShellRuntimeContext,
  private val generatorsExecutor: ShellDataGeneratorsExecutor
) {
  suspend fun getSuggestionsOfNext(node: ShellCommandTreeNode<*>): List<ShellCompletionSuggestion> {
    val suggestions = when (node) {
      is ShellCommandNode -> getSuggestionsForSubcommand(node)
      is ShellOptionNode -> getSuggestionsForOption(node)
      is ShellArgumentNode -> node.parent?.let { getSuggestionsOfNext(it) } ?: emptyList()
      is ShellUnknownNode -> node.parent?.let { getSuggestionsOfNext(it) } ?: emptyList()
      else -> emptyList()
    }
    return filterSuggestionsByPrefix(suggestions.distinctBy { it.name })
  }

  suspend fun getDirectSuggestionsOfNext(option: ShellOptionNode): List<ShellCompletionSuggestion> {
    val availableArgs = getAvailableArguments(option)
    val argumentSuggestions = availableArgs.flatMap { getArgumentSuggestions(it) }.distinctBy { it.name }
    return filterSuggestionsByPrefix(argumentSuggestions)
  }

  fun getAvailableArguments(node: ShellOptionNode): List<ShellArgumentSpec> {
    return node.getAvailableArguments(node.spec.arguments)
  }

  private fun getAvailableArguments(node: ShellCommandNode): List<ShellArgumentSpec> {
    return node.getAvailableArguments(node.spec.arguments)
  }

  private fun ShellCommandTreeNode<*>.getAvailableArguments(allArgs: List<ShellArgumentSpec>): List<ShellArgumentSpec> {
    val existingArgs = children.mapNotNull { (it as? ShellArgumentNode)?.spec }
    val lastExistingArg = existingArgs.lastOrNull()
    val lastExistingArgIndex = lastExistingArg?.let { allArgs.indexOf(it) } ?: -1
    val firstRequiredArgIndex = allArgs.withIndex().find { (ind, argument) ->
      ind > lastExistingArgIndex && !argument.isOptional
    }?.index ?: (allArgs.size - 1)
    val includeLast = lastExistingArg?.isVariadic == true
    return allArgs.subList(lastExistingArgIndex + if (includeLast) 0 else 1, firstRequiredArgIndex + 1)
  }

  private suspend fun getSuggestionsForSubcommand(node: ShellCommandNode): List<ShellCompletionSuggestion> {
    val suggestions = mutableListOf<ShellCompletionSuggestion>()

    val spec = node.spec
    if (node.children.isEmpty()) {
      suggestions.addAll(spec.getSubcommands())
    }

    if (spec.requiresSubcommand) {
      return suggestions
    }

    val lastArg = (node.children.lastOrNull() as? ShellArgumentNode)?.spec
    if ((!node.getMergedParserOptions().optionsMustPrecedeArguments || node.children.filterIsInstance<ShellArgumentNode>().isEmpty())
        && (lastArg?.isVariadic != true || lastArg.optionsCanBreakVariadicArg)) {
      val options = getAvailableOptions(node)
      suggestions.addAll(options)
    }

    val availableArgs = getAvailableArguments(node)
    suggestions.addAll(availableArgs.flatMap { getArgumentSuggestions(it) })
    return suggestions
  }

  private suspend fun getAvailableOptions(node: ShellCommandNode): List<ShellOptionSpec> {
    val existingOptions = node.children.mapNotNull { (it as? ShellOptionNode)?.spec }
    return getAllOptions(node).filter { opt ->
      (opt.repeatTimes == 0 || existingOptions.count { it == opt } < opt.repeatTimes)
      && opt.exclusiveOn.none { exclusive -> existingOptions.any { it.name == exclusive } }
      && opt.dependsOn.all { dependant -> existingOptions.any { it.name == dependant } }
    }
  }

  private suspend fun getAllOptions(node: ShellCommandNode): List<ShellOptionSpec> {
    val options = mutableListOf<ShellOptionSpec>()
    options.addAll(node.spec.getAllOptions())

    /**
     * Checks that [parent] command contain the subcommand with the name of [child].
     * If there is no such subcommand, it means that [child] is a nested command in a place of argument with command suggestions.
     */
    suspend fun isSubcommand(parent: ShellCommandNode, child: ShellCommandNode): Boolean {
      val subcommands = parent.spec.getSubcommands()
      return subcommands.find { subCmd -> child.spec.name == subCmd.name } != null
    }

    var child = node
    var parent = node.parent
    // parent commands can define 'persistent' options - they can be used in all subcommands
    // but add persistent options from parent, only if it is a direct subcommand
    while (parent is ShellCommandNode && isSubcommand(parent, child)) {
      val parentOptions = parent.spec.getAllOptions()
      options.addAll(parentOptions.filter { it.isPersistent })
      child = parent
      parent = parent.parent
    }
    return options
  }

  private suspend fun getSuggestionsForOption(node: ShellOptionNode): List<ShellCompletionSuggestion> {
    val suggestions = mutableListOf<ShellCompletionSuggestion>()

    val directSuggestions = getDirectSuggestionsOfNext(node)
    suggestions.addAll(directSuggestions)

    val availableArgs = getAvailableArguments(node)
    val lastArg = (node.children.lastOrNull() as? ShellArgumentNode)?.spec
    // suggest parent options and args if there is no required args or last arg is required, but variadic
    val args = if (lastArg?.isVariadic == true && lastArg.optionsCanBreakVariadicArg) availableArgs - lastArg else availableArgs
    if (node.parent is ShellCommandNode && args.all { it.isOptional }) {
      val parentSuggestions = getSuggestionsForSubcommand(node.parent)
      suggestions.addAll(parentSuggestions)
    }
    return suggestions
  }

  private suspend fun getArgumentSuggestions(arg: ShellArgumentSpec): List<ShellCompletionSuggestion> {
    val suggestions = mutableListOf<ShellCompletionSuggestion>()
    for (generator in arg.generators) {
      val result = generatorsExecutor.execute(context, generator) ?: emptyList()
      // Wrap ordinary completion suggestions into ShellArgumentSuggestion to be able to get the argument spec in ShellCommandTreeBuilder
      val adjustedSuggestions = result.map { if (it !is ShellCommandSpec && it !is ShellAliasSuggestion) ShellArgumentSuggestion(it, arg) else it }
      suggestions.addAll(adjustedSuggestions)
    }
    return suggestions
  }

  /**
   * Filter out less specific suggestions if there is a suggestion for prefix shorter than [ShellRuntimeContext.typedPrefix] in [context].
   */
  private fun filterSuggestionsByPrefix(suggestions: List<ShellCompletionSuggestion>): List<ShellCompletionSuggestion> {
    if (suggestions.isEmpty()) {
      return emptyList()
    }
    // Bigger the prefix replacement index, more specific the suggestion
    val prefixReplacementIndex = suggestions.maxOf { it.prefixReplacementIndex }
    return if (prefixReplacementIndex == 0) {
      suggestions
    }
    else suggestions.filter { it.prefixReplacementIndex == prefixReplacementIndex }
  }

  private suspend fun ShellCommandSpec.getSubcommands(): List<ShellCommandSpec> {
    return generatorsExecutor.execute(context, subcommandsGenerator) ?: emptyList()
  }

  private suspend fun ShellCommandSpec.getAllOptions(): List<ShellOptionSpec> {
    return generatorsExecutor.execute(context, allOptionsGenerator) ?: emptyList()
  }
}
