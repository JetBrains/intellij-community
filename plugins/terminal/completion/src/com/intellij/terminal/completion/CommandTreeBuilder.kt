// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.completion

import org.jetbrains.terminal.completion.BaseSuggestion
import org.jetbrains.terminal.completion.ShellCommand
import org.jetbrains.terminal.completion.ShellOption
import java.io.File

internal class CommandTreeBuilder private constructor(
  private val suggestionsProvider: CommandTreeSuggestionsProvider,
  private val commandSpecManager: CommandSpecManager,
  private val arguments: List<String>
) {
  companion object {
    suspend fun build(suggestionsProvider: CommandTreeSuggestionsProvider,
              commandSpecManager: CommandSpecManager,
              command: String,
              commandSpec: ShellCommand,
              arguments: List<String>): SubcommandNode {
      val builder = CommandTreeBuilder(suggestionsProvider, commandSpecManager, arguments)
      val root = builder.createSubcommandNode(command, commandSpec, null)
      builder.buildSubcommandTree(root)
      return root
    }
  }

  private var curIndex = 0

  private suspend fun buildSubcommandTree(root: SubcommandNode) {
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
        val node = suggestion?.let { createChildNode(name, it, root) } ?: UnknownNode(name, root)
        root.children.add(node)
        curIndex++
        if (node is SubcommandNode) {
          buildSubcommandTree(node)
        }
        else if (node is OptionNode) {
          buildOptionTree(node)
        }
      }
    }
  }

  private suspend fun buildOptionTree(root: OptionNode) {
    while (curIndex < arguments.size) {
      val name = arguments[curIndex]
      val suggestions = suggestionsProvider.getDirectSuggestionsOfNext(root, name)
      val suggestion = suggestions.find { it.names.contains(name) }
      val node = if (suggestion == null) {
        // option requires an argument, then probably provided name is this argument
        suggestionsProvider.getAvailableArguments(root).find { !it.isOptional }?.let {
          ArgumentNode(name, it, root)
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
  private suspend fun addChainedOptions(root: SubcommandNode, suggestions: List<BaseSuggestion>, options: String) {
    val flags = options.removePrefix("-").toCharArray().map { "-$it" }
    for (flag in flags) {
      val option = suggestions.find { it.names.contains(flag) }
      val node = if (option != null) {
        createChildNode(flag, option, root)
      }
      else UnknownNode(flag, root)
      root.children.add(node)
    }
  }

  private fun tryAddOptionWithSeparator(root: SubcommandNode, suggestions: List<BaseSuggestion>, name: String): Boolean {
    return suggestions.mapNotNull { s ->
      val option = s as? ShellOption ?: return@mapNotNull null
      val separator = option.separator ?: return@mapNotNull null
      val optName = option.names.find { name.startsWith(it + separator) } ?: return@mapNotNull null
      val argValue = name.removePrefix(optName + separator)
      Triple(option, optName, argValue)
    }.firstOrNull()?.let { (option, optName, argValue) ->
      val optionNode = OptionNode(optName, option, root)
      root.children.add(optionNode)
      if (argValue.isNotEmpty() && option.args.isNotEmpty()) {
        val argNode = ArgumentNode(argValue, option.args.first(), optionNode)
        optionNode.children.add(argNode)
      }
      else if (argValue.isNotEmpty()) {
        // strange case: argument is provided, but isn't mentioned in the option spec
        optionNode.children.add(UnknownNode(argValue, optionNode))
      }
      true
    } ?: false
  }

  private suspend fun createChildNode(name: String, suggestion: BaseSuggestion, parent: CommandPartNode<*>?): CommandPartNode<*> {
    return when (suggestion) {
      is ShellCommand -> createSubcommandNode(name, suggestion, parent)
      is ShellOption -> OptionNode(name, suggestion, parent)
      is ShellArgumentSuggestion -> ArgumentNode(name, suggestion.argument, parent)
      else -> throw IllegalArgumentException("Unknown suggestion: $suggestion")
    }
  }

  private suspend fun createSubcommandNode(name: String, subcommand: ShellCommand, parent: CommandPartNode<*>?): SubcommandNode {
    val spec = getLoadedCommandSpec(subcommand)
    return SubcommandNode(name, spec, parent)
  }

  private suspend fun getLoadedCommandSpec(spec: ShellCommand): ShellCommand {
    val specRef = spec.loadSpec ?: return spec
    return commandSpecManager.getCommandSpec(specRef) ?: spec
  }
}
