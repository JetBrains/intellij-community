// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.block.completion.engine

import com.intellij.terminal.block.completion.ShellArgumentSuggestion
import com.intellij.terminal.block.completion.ShellCommandSpecsManager
import com.intellij.terminal.block.completion.ShellDataGeneratorsExecutor
import com.intellij.terminal.block.completion.ShellRuntimeContextProvider
import com.intellij.terminal.block.completion.spec.ShellCommandSpec
import com.intellij.terminal.block.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.block.completion.spec.ShellOptionSpec
import java.io.File

internal class ShellCommandTreeBuilder private constructor(
  command: String,
  private val contextProvider: ShellRuntimeContextProvider,
  private val generatorsExecutor: ShellDataGeneratorsExecutor,
  private val commandSpecManager: ShellCommandSpecsManager,
  private val arguments: List<String>
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
      val builder = ShellCommandTreeBuilder(command, contextProvider, generatorsExecutor, commandSpecManager, arguments)
      val root = builder.createSubcommandNode(command, commandSpec, null)
      builder.buildSubcommandTree(root)
      return root
    }
  }

  private var curIndex = 0
  private var commandText: String = command

  private suspend fun buildSubcommandTree(root: ShellCommandNode) {
    while (curIndex < arguments.size) {
      val name = arguments[curIndex]
      commandText += " $name"
      val suggestionsProvider = createSuggestionsProvider(name)
      val suggestions = suggestionsProvider.getSuggestionsOfNext(root, name)
      var suggestion = suggestions.find { it.names.contains(name) }
      if (suggestion == null && name.contains(File.separatorChar)) {
        // most probably it is a file path
        val fileName = name.substringAfterLast(File.separatorChar)
        suggestion = suggestions.find { s -> s.names.find { it == fileName || it == "$fileName${File.separatorChar}" } != null }
      }
      if (suggestion == null
          && !root.getMergedParserDirectives().flagsArePosixNonCompliant
          && name.startsWith("-") && !name.startsWith("--") && name.length > 2) {
        addChainedOptions(root, suggestions, name)
        curIndex++
      }
      else if (suggestion == null && tryAddOptionWithSeparator(root, suggestions, name)) {
        curIndex++
      }
      else {
        val node = suggestion?.let { createChildNode(name, it, root) } ?: ShellUnknownNode(name, root)
        root.children.add(node)
        curIndex++
        if (node is ShellCommandNode) {
          buildSubcommandTree(node)
        }
        else if (node is ShellOptionNode) {
          buildOptionTree(node)
        }
      }
    }
  }

  private suspend fun buildOptionTree(root: ShellOptionNode) {
    while (curIndex < arguments.size) {
      val name = arguments[curIndex]
      commandText += " $name"
      val suggestionsProvider = createSuggestionsProvider(name)
      val suggestions = suggestionsProvider.getDirectSuggestionsOfNext(root)
      val suggestion = suggestions.find { it.names.contains(name) }
      val node = if (suggestion == null) {
        // option requires an argument, then probably provided name is this argument
        suggestionsProvider.getAvailableArguments(root).find { !it.isOptional }?.let {
          ShellArgumentNode(name, it, root)
        }
      }
      else {
        createChildNode(name, suggestion, root)
      }
      if (node != null) {
        root.children.add(node)
        curIndex++
      }
      else return
    }
  }

  /** [options] is a posix chained options string, for example -abcde */
  private suspend fun addChainedOptions(root: ShellCommandNode, suggestions: List<ShellCompletionSuggestion>, options: String) {
    val flags = options.removePrefix("-").toCharArray().map { "-$it" }
    for (flag in flags) {
      val option = suggestions.find { it.names.contains(flag) }
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
      val optName = option.names.find { name.startsWith(it + separator) } ?: return@mapNotNull null
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
      else -> throw IllegalArgumentException("Unknown suggestion: $suggestion")
    }
  }

  private suspend fun createSubcommandNode(name: String, subcommand: ShellCommandSpec, parent: ShellCommandTreeNode<*>?): ShellCommandNode {
    val spec = commandSpecManager.getFullCommandSpec(subcommand)
    return ShellCommandNode(name, spec, parent)
  }

  private fun createSuggestionsProvider(typedPrefix: String): ShellCommandTreeSuggestionsProvider {
    val context = contextProvider.getContext(commandText, typedPrefix)
    return ShellCommandTreeSuggestionsProvider(context, generatorsExecutor)
  }
}
