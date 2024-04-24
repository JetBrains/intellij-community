// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.block.completion.engine

import com.intellij.terminal.block.completion.ShellArgumentSuggestion
import com.intellij.terminal.block.completion.ShellCommandSpecsManager
import org.jetbrains.terminal.completion.BaseSuggestion
import org.jetbrains.terminal.completion.ShellCommand
import org.jetbrains.terminal.completion.ShellOption
import java.io.File

internal class ShellCommandTreeBuilder private constructor(
  private val suggestionsProvider: ShellCommandTreeSuggestionsProvider,
  private val commandSpecManager: ShellCommandSpecsManager,
  private val arguments: List<String>
) {
  companion object {
    suspend fun build(suggestionsProvider: ShellCommandTreeSuggestionsProvider,
                      commandSpecManager: ShellCommandSpecsManager,
                      command: String,
                      commandSpec: ShellCommand,
                      arguments: List<String>): ShellCommandNode {
      val builder = ShellCommandTreeBuilder(suggestionsProvider, commandSpecManager, arguments)
      val root = builder.createSubcommandNode(command, commandSpec, null)
      builder.buildSubcommandTree(root)
      return root
    }
  }

  private var curIndex = 0

  private suspend fun buildSubcommandTree(root: ShellCommandNode) {
    while (curIndex < arguments.size) {
      val name = arguments[curIndex]
      val suggestions = suggestionsProvider.getSuggestionsOfNext(root, name)
      var suggestion = suggestions.find { it.names.contains(name) }
      if (suggestion == null && name.contains(File.separatorChar)) {
        // most probably it is a file path
        val fileName = name.substringAfterLast(File.separatorChar)
        suggestion = suggestions.find { s -> s.names.find { it == fileName || it == "$fileName${File.separatorChar}" } != null }
      }
      if (suggestion == null
          && !root.getMergedParserDirectives().flagsArePosixNoncompliant
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
      val suggestions = suggestionsProvider.getDirectSuggestionsOfNext(root, name)
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
  private suspend fun addChainedOptions(root: ShellCommandNode, suggestions: List<BaseSuggestion>, options: String) {
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

  private fun tryAddOptionWithSeparator(root: ShellCommandNode, suggestions: List<BaseSuggestion>, name: String): Boolean {
    return suggestions.mapNotNull { s ->
      val option = s as? ShellOption ?: return@mapNotNull null
      val separator = option.separator ?: return@mapNotNull null
      val optName = option.names.find { name.startsWith(it + separator) } ?: return@mapNotNull null
      val argValue = name.removePrefix(optName + separator)
      Triple(option, optName, argValue)
    }.firstOrNull()?.let { (option, optName, argValue) ->
      val optionNode = ShellOptionNode(optName, option, root)
      root.children.add(optionNode)
      if (argValue.isNotEmpty() && option.args.isNotEmpty()) {
        val argNode = ShellArgumentNode(argValue, option.args.first(), optionNode)
        optionNode.children.add(argNode)
      }
      else if (argValue.isNotEmpty()) {
        // strange case: argument is provided, but isn't mentioned in the option spec
        optionNode.children.add(ShellUnknownNode(argValue, optionNode))
      }
      true
    } ?: false
  }

  private suspend fun createChildNode(name: String, suggestion: BaseSuggestion, parent: ShellCommandTreeNode<*>?): ShellCommandTreeNode<*> {
    return when (suggestion) {
      is ShellCommand -> createSubcommandNode(name, suggestion, parent)
      is ShellOption -> ShellOptionNode(name, suggestion, parent)
      is ShellArgumentSuggestion -> ShellArgumentNode(name, suggestion.argument, parent)
      else -> throw IllegalArgumentException("Unknown suggestion: $suggestion")
    }
  }

  private suspend fun createSubcommandNode(name: String, subcommand: ShellCommand, parent: ShellCommandTreeNode<*>?): ShellCommandNode {
    val spec = getLoadedCommandSpec(subcommand)
    return ShellCommandNode(name, spec, parent)
  }

  private suspend fun getLoadedCommandSpec(spec: ShellCommand): ShellCommand {
    val specRef = spec.loadSpec ?: return spec
    return commandSpecManager.getCommandSpec(specRef) ?: spec
  }
}
