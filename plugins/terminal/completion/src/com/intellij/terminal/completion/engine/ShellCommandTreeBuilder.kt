// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.completion.engine

import com.intellij.terminal.completion.ShellArgumentSuggestion
import com.intellij.terminal.completion.ShellCommandSpecsManager
import com.intellij.terminal.completion.ShellDataGeneratorsExecutor
import com.intellij.terminal.completion.ShellRuntimeContextProvider
import com.intellij.terminal.completion.spec.ShellAliasSuggestion
import com.intellij.terminal.completion.spec.ShellCommandSpec
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.completion.spec.ShellOptionSpec
import com.intellij.util.execution.ParametersListUtil

internal class ShellCommandTreeBuilder private constructor(
  private val contextProvider: ShellRuntimeContextProvider,
  private val generatorsExecutor: ShellDataGeneratorsExecutor,
  private val commandSpecManager: ShellCommandSpecsManager,
  initialArguments: List<String>
) {
  companion object {
    suspend fun build(
      contextProvider: ShellRuntimeContextProvider,
      generatorsExecutor: ShellDataGeneratorsExecutor,
      commandSpecManager: ShellCommandSpecsManager,
      command: String,
      commandSpec: ShellCommandSpec,
      arguments: List<String>
    ): ShellCommandNode {
      val builder = ShellCommandTreeBuilder(contextProvider, generatorsExecutor, commandSpecManager, arguments)
      val root = builder.createSubcommandNode(command, commandSpec, null)
      builder.buildSubcommandTree(root)
      return root
    }

    /**
     * Maximum number of times an alias may be resolved while building the tree.
     * This limit prevents infinite alias expansion in case of some recursive definition.
     *
     * Examples of recursive aliasing:
     *  - git test1 -> git test2, git test2 -> git test1
     *  - git test -> git repeat test, git repeat test -> git test
     *  - git test1 -> git -v test1
     *    becomes
     *    git test1 -> git -v test1 -> git -v -v test1 -> git -v -v -v test1 -> ...
     */
    private const val MAX_ALIAS_RESOLUTIONS = 10
  }

  private var curIndex = 0
  private var aliasesResolved = 0
  private var arguments = initialArguments

  private suspend fun buildSubcommandTree(root: ShellCommandNode) {
    while (curIndex < arguments.size) {
      val curArgument = arguments[curIndex]
      val name = curArgument.removeQuotes()
      val currentTokens = arguments.take(curIndex + 1)
      val suggestionsProvider = createSuggestionsProvider(currentTokens)
      val suggestions = suggestionsProvider.getSuggestionsOfNext(root)
      val suggestion = findMatchingSuggestion(suggestions, name)
      if (suggestion == null
          && !root.getMergedParserOptions().flagsArePosixNonCompliant
          && name.startsWith("-") && !name.startsWith("--") && name.length > 2) {
        addChainedOptions(root, suggestions, name)
        curIndex++
      }
      else if (suggestion == null && tryAddOptionWithSeparator(root, suggestions, name)) {
        curIndex++
      }
      else {
        val node = suggestion?.let { createChildNode(curArgument, it, root) }
                   ?: ShellUnknownNode(curArgument, root)
        if (node !is ShellAliasNode) {
          root.children.add(node)
          curIndex++
        }
        when (node) {
          is ShellCommandNode -> buildSubcommandTree(node)
          is ShellOptionNode -> buildOptionTree(node)
          is ShellAliasNode -> {
            val newArgs = ParametersListUtil.parse(node.spec.aliasValue)
            arguments = arguments.take(curIndex) + newArgs + arguments.drop(curIndex + 1)
            aliasesResolved++
          }
        }
      }
    }
  }

  private suspend fun buildOptionTree(root: ShellOptionNode) {
    while (curIndex < arguments.size) {
      val curArgument = arguments[curIndex]
      val name = curArgument.removeQuotes()
      val currentTokens = arguments.take(curIndex + 1)
      val suggestionsProvider = createSuggestionsProvider(currentTokens)
      val suggestions = suggestionsProvider.getDirectSuggestionsOfNext(root)
      val suggestion = findMatchingSuggestion(suggestions, name)
      val node = if (suggestion == null) {
        // option requires an argument, then probably provided name is this argument
        suggestionsProvider.getAvailableArguments(root).find { !it.isOptional }?.let {
          ShellArgumentNode(curArgument, it, root)
        }
      }
      else {
        createChildNode(curArgument, suggestion, root)
      }
      if (node != null) {
        root.children.add(node)
        curIndex++
      }
      else return
    }
  }

  private fun findMatchingSuggestion(suggestions: List<ShellCompletionSuggestion>, name: String): ShellCompletionSuggestion? {
    if (suggestions.isEmpty()) return null
    // It is assumed that all suggestions are with the same prefixReplacementIndex
    val prefixReplacementIndex = suggestions.first().prefixReplacementIndex
    val prefixToMatch = name.substring(prefixReplacementIndex)
    return suggestions.find { it.name == prefixToMatch }
  }

  /** [options] is a posix chained options string, for example -abcde */
  private suspend fun addChainedOptions(root: ShellCommandNode, suggestions: List<ShellCompletionSuggestion>, options: String) {
    val flags = options.removePrefix("-").toCharArray().map { "-$it" }
    for (flag in flags) {
      val option = suggestions.find { it.name == flag }
      val node = if (option != null) {
        createChildNode(flag, option, root)
      }
      else ShellUnknownNode(flag, root)
      root.children.add(node)
    }
  }

  private fun tryAddOptionWithSeparator(root: ShellCommandNode, suggestions: List<ShellCompletionSuggestion>, name: String): Boolean {
    return suggestions.mapNotNull { s ->
      val option = s as? ShellOptionSpec ?: return@mapNotNull null
      val separator = option.separator ?: return@mapNotNull null
      val optName = option.name.takeIf { name.startsWith(it + separator) } ?: return@mapNotNull null
      val argValue = name.removePrefix(optName + separator)
      Triple(option, optName, argValue)
    }.firstOrNull()?.let { (option, optName, argValue) ->
      val optionNode = ShellOptionNode(optName, option, root)
      root.children.add(optionNode)
      if (argValue.isNotEmpty() && option.arguments.isNotEmpty()) {
        val argNode = ShellArgumentNode(argValue, option.arguments.first(), optionNode)
        optionNode.children.add(argNode)
      }
      else if (argValue.isNotEmpty()) {
        // strange case: argument is provided, but isn't mentioned in the option spec
        optionNode.children.add(ShellUnknownNode(argValue, optionNode))
      }
      true
    } ?: false
  }

  private suspend fun createChildNode(name: String, suggestion: ShellCompletionSuggestion, parent: ShellCommandTreeNode<*>?): ShellCommandTreeNode<*> {
    return when (suggestion) {
      is ShellCommandSpec -> createSubcommandNode(name, suggestion, parent)
      is ShellOptionSpec -> ShellOptionNode(name, suggestion, parent)
      is ShellArgumentSuggestion -> ShellArgumentNode(name, suggestion.argument, parent)
      is ShellAliasSuggestion ->
        if (aliasesResolved < MAX_ALIAS_RESOLUTIONS) ShellAliasNode(name, suggestion, parent)
        else ShellUnknownNode(name, parent)
      else -> throw IllegalArgumentException("Unknown suggestion: $suggestion")
    }
  }

  private suspend fun createSubcommandNode(name: String, subcommand: ShellCommandSpec, parent: ShellCommandTreeNode<*>?): ShellCommandNode {
    val spec = commandSpecManager.getFullCommandSpec(subcommand)
    return ShellCommandNode(name, spec, parent)
  }

  private fun createSuggestionsProvider(commandTokens: List<String>): ShellCommandTreeSuggestionsProvider {
    val context = contextProvider.getContext(commandTokens)
    return ShellCommandTreeSuggestionsProvider(context, generatorsExecutor)
  }

  private fun String.removeQuotes(): String {
    return if (startsWith('"') && endsWith('"')
               || startsWith("'") && endsWith("'")) {
      if (length > 1) substring(1, length - 1) else ""
    }
    else this
  }
}
