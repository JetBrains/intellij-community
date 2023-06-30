// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

internal class CommandTreeSuggestionsProvider(private val runtimeDataProvider: ShellRuntimeDataProvider) {
  fun getSuggestionsOfNext(node: CommandPartNode<*>, nextNodeText: String): List<BaseSuggestion> {
    return when (node) {
      is SubcommandNode -> getSuggestionsForSubcommand(node, nextNodeText)
      is OptionNode -> getSuggestionsForOption(node, nextNodeText)
      is ArgumentNode -> node.parent?.let { getSuggestionsOfNext(it, nextNodeText) } ?: emptyList()
      else -> emptyList()
    }
  }

  fun getDirectSuggestionsOfNext(option: OptionNode, nextNodeText: String): List<BaseSuggestion> {
    val availableArgs = getAvailableArguments(option)
    return availableArgs.flatMap { getArgumentSuggestions(it, nextNodeText) }
  }

  fun getAvailableArguments(node: OptionNode): List<ShellArgument> {
    return node.getAvailableArguments(node.spec.args)
  }

  private fun getAvailableArguments(node: SubcommandNode): List<ShellArgument> {
    return node.getAvailableArguments(node.spec.args)
  }

  private fun CommandPartNode<*>.getAvailableArguments(allArgs: List<ShellArgument>): List<ShellArgument> {
    val existingArgs = children.mapNotNull { (it as? ArgumentNode)?.spec }
    val lastExistingArg = existingArgs.lastOrNull()
    val lastExistingArgIndex = lastExistingArg?.let { allArgs.indexOf(it) } ?: -1
    val firstRequiredArgIndex = allArgs.withIndex().find { (ind, argument) ->
      ind > lastExistingArgIndex && !argument.isOptional
    }?.index ?: (allArgs.size - 1)
    val includeLast = lastExistingArg?.isVariadic == true
    return allArgs.subList(lastExistingArgIndex + if (includeLast) 0 else 1, firstRequiredArgIndex + 1)
  }

  private fun getSuggestionsForSubcommand(node: SubcommandNode, nextNodeText: String): List<BaseSuggestion> {
    val spec = node.spec
    val suggestions = mutableListOf<BaseSuggestion>()
    if (node.children.isEmpty()) {
      suggestions.addAll(spec.subcommands)
    }

    if (spec.requiresSubcommand) {
      return suggestions
    }

    val lastArg = (node.children.lastOrNull() as? ArgumentNode)?.spec
    if ((!node.getMergedParserDirectives().optionsMustPrecedeArguments || node.children.filterIsInstance<ArgumentNode>().isEmpty())
        && (lastArg?.isVariadic != true || lastArg.optionsCanBreakVariadicArg)) {
      val options = getAvailableOptions(node)
      suggestions.addAll(options)
    }

    val availableArgs = getAvailableArguments(node)
    suggestions.addAll(availableArgs.flatMap { getArgumentSuggestions(it, nextNodeText) })
    return suggestions
  }

  private fun getAvailableOptions(node: SubcommandNode): List<ShellOption> {
    val existingOptions = node.children.mapNotNull { (it as? OptionNode)?.spec }
    return getAllOptions(node).filter { opt ->
      (opt.repeatTimes == 0 || existingOptions.count { it == opt } < opt.repeatTimes)
      && opt.exclusiveOn.none { exclusive -> existingOptions.any { it.names.contains(exclusive) } }
      && opt.dependsOn.all { dependant -> existingOptions.any { it.names.contains(dependant) } }
    }
  }

  private fun getAllOptions(node: SubcommandNode): List<ShellOption> {
    val options = mutableListOf<ShellOption>()
    options.addAll(node.spec.options)
    var cur = node.parent
    while (cur is SubcommandNode) {
      // parent commands can define 'persistent' options - they can be used in all nested subcommands
      options.addAll(cur.spec.options.filter { it.isPersistent })
      cur = cur.parent
    }
    return options
  }

  private fun getSuggestionsForOption(node: OptionNode, nextNodeText: String): List<BaseSuggestion> {
    val suggestions = mutableListOf<BaseSuggestion>()
    val directSuggestions = getDirectSuggestionsOfNext(node, nextNodeText)
    suggestions.addAll(directSuggestions)

    val availableArgs = getAvailableArguments(node)
    val lastArg = (node.children.lastOrNull() as? ArgumentNode)?.spec
    // suggest parent options and args if there is no required args or last arg is required, but variadic
    val args = if (lastArg?.isVariadic == true && lastArg.optionsCanBreakVariadicArg) availableArgs - lastArg else availableArgs
    if (node.parent is SubcommandNode && args.all { it.isOptional }) {
      val parentSuggestions = getSuggestionsForSubcommand(node.parent, nextNodeText)
      suggestions.addAll(parentSuggestions)
    }
    return suggestions
  }

  private fun getArgumentSuggestions(arg: ShellArgument, nextNodeText: String): List<ShellArgumentSuggestion> {
    val suggestions = mutableListOf<ShellArgumentSuggestion>()
    suggestions.addAll(arg.suggestions.map { ShellArgumentSuggestion(it, arg) })

    val templates = mutableSetOf<String>()
    templates.addAll(arg.templates)
    templates.addAll(arg.generators.flatMap { it.templates })
    val suggestAllFiles = templates.contains("filepaths")
    val suggestFolders = templates.contains("folders")
    if (suggestAllFiles || suggestFolders) {
      val fileSuggestions = getFileSuggestions(arg, nextNodeText, onlyDirectories = suggestFolders && !suggestAllFiles)
      suggestions.addAll(fileSuggestions)
    }
    return suggestions
  }

  private fun getFileSuggestions(arg: ShellArgument, nextNodeText: String, onlyDirectories: Boolean): List<ShellArgumentSuggestion> {
    val basePath = if (nextNodeText.contains('/')) {
      nextNodeText.substringBeforeLast('/') + "/"
    }
    else "."
    val files = runtimeDataProvider.getFilesFromDirectory(basePath)
    return files.asSequence()
      .filter { !onlyDirectories || it.endsWith('/') }
      .map { ShellArgumentSuggestion(ShellSuggestion(names = listOf(it)), arg) }
      .toList()
  }
}