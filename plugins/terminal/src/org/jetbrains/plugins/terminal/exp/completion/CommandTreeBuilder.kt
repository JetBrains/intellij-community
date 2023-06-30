// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger

internal class CommandTreeBuilder private constructor(
  private val suggestionsProvider: CommandTreeSuggestionsProvider,
  private val commandSpecManager: CommandSpecManager,
  private val arguments: List<String>
) {
  companion object {
    fun build(suggestionsProvider: CommandTreeSuggestionsProvider,
              commandSpecManager: CommandSpecManager,
              command: String,
              commandSpec: ShellSubcommand,
              arguments: List<String>): SubcommandNode {
      val builder = CommandTreeBuilder(suggestionsProvider, commandSpecManager, arguments)
      val root = builder.createSubcommandNode(command, commandSpec, null)
      builder.buildSubcommandTree(root)
      return root
    }

    private val LOG: Logger = logger<CommandTreeBuilder>()
  }

  private var curIndex = 0

  private fun buildSubcommandTree(root: SubcommandNode) {
    while (curIndex < arguments.size) {
      val name = arguments[curIndex]
      val suggestions = suggestionsProvider.getSuggestionsOfNext(root, name)
      val suggestion = suggestions.find { it.names.contains(name) }
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

  private fun buildOptionTree(root: OptionNode) {
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
  private fun addChainedOptions(root: SubcommandNode, suggestions: List<BaseSuggestion>, options: String) {
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

  private fun createChildNode(name: String, suggestion: BaseSuggestion, parent: CommandPartNode<*>?): CommandPartNode<*> {
    return when (suggestion) {
      is ShellSubcommand -> createSubcommandNode(name, suggestion, parent)
      is ShellOption -> OptionNode(name, suggestion, parent)
      is ShellArgumentSuggestion -> ArgumentNode(name, suggestion.argument, parent)
      else -> throw IllegalArgumentException("Unknown suggestion: $suggestion")
    }
  }

  private fun createSubcommandNode(name: String, subcommand: ShellSubcommand, parent: CommandPartNode<*>?): SubcommandNode {
    val spec = getLoadedCommandSpec(subcommand)
    return SubcommandNode(name, spec, parent)
  }

  private fun getLoadedCommandSpec(spec: ShellSubcommand): ShellSubcommand {
    val specRef = spec.loadSpec ?: return spec
    val loadedSpec = commandSpecManager.getCommandSpec(specRef)
    return if (loadedSpec != null) {
      loadedSpec
    }
    else {
      LOG.warn("Failed to find spec: $specRef")
      spec
    }
  }
}
