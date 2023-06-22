// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

internal class CommandTreeBuilder(private val command: String,
                                  private val commandSpec: ShellSubcommand,
                                  private val arguments: List<String>) {
  private var curIndex = 0

  fun build(): SubcommandNode {
    val root = SubcommandNode(command, commandSpec, null)
    buildSubcommandTree(root)
    return root
  }

  private fun buildSubcommandTree(root: SubcommandNode) {
    while (curIndex < arguments.size) {
      val name = arguments[curIndex]
      val suggestions = root.getSuggestionsOfNext()
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
      val suggestions = root.getDirectSuggestionsOfNext()
      val suggestion = suggestions.find { it.names.contains(name) }
      val node = if (suggestion == null) {
        // option requires an argument, then probably provided name is this argument
        root.getAvailableArguments(root.spec.args).find { !it.isOptional }?.let {
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
      is ShellSubcommand -> SubcommandNode(name, suggestion, parent)
      is ShellOption -> OptionNode(name, suggestion, parent)
      is ShellArgumentSuggestion -> ArgumentNode(name, suggestion.argument, parent)
      else -> throw IllegalArgumentException("Unknown suggestion: $suggestion")
    }
  }
}
