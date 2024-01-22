// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.completion

import com.intellij.terminal.completion.CommandSpecCompletionUtil.isFilePath
import com.intellij.terminal.completion.CommandSpecCompletionUtil.isFolder
import org.jetbrains.terminal.completion.*
import java.io.File

internal class CommandTreeSuggestionsProvider(
  private val commandSpecManager: CommandSpecManager,
  private val runtimeDataProvider: ShellRuntimeDataProvider
) {
  suspend fun getSuggestionsOfNext(node: CommandPartNode<*>, nextNodeText: String): List<BaseSuggestion> {
    return when (node) {
      is SubcommandNode -> getSuggestionsForSubcommand(node, nextNodeText)
      is OptionNode -> getSuggestionsForOption(node, nextNodeText)
      is ArgumentNode -> node.parent?.let { getSuggestionsOfNext(it, nextNodeText) } ?: emptyList()
      else -> emptyList()
    }
  }

  suspend fun getDirectSuggestionsOfNext(option: OptionNode, nextNodeText: String): List<BaseSuggestion> {
    val availableArgs = getAvailableArguments(option)
    return availableArgs.flatMap { getArgumentSuggestions(it, nextNodeText) }
  }

  /**
   * Returns the list of the commands and aliases available in the Shell.
   * Returned [ShellCommand] objects contain only names, descriptions, and a 'loadSpec' reference to load full command spec (if it exists).
   */
  suspend fun getAvailableCommands(): List<ShellCommand> {
    val shellEnv = runtimeDataProvider.getShellEnvironment() ?: return emptyList()
    val commands = sequence {
      yieldAll(shellEnv.keywords)
      yieldAll(shellEnv.builtins)
      yieldAll(shellEnv.functions)
      yieldAll(shellEnv.commands)
    }.map {
      commandSpecManager.getShortCommandSpec(it) ?: ShellCommand(names = listOf(it))
    }
    val aliases = shellEnv.aliases.asSequence().map { (alias, command) ->
      ShellCommand(names = listOf(alias), description = """Alias for "${command}"""")
    }
    // place aliases first, so the alias will have preference over the command, if there is the command with the same name
    return (aliases + commands).distinctBy { it.names.single() }.toList()
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

  private suspend fun getSuggestionsForSubcommand(node: SubcommandNode, nextNodeText: String): List<BaseSuggestion> {
    val suggestions = mutableListOf<BaseSuggestion>()

    // suggest subcommands and options only if the provided value is not a file path
    if (!nextNodeText.contains(File.separatorChar)) {
      val spec = node.spec
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

    /**
     * Checks that [parent] command contain the subcommand with the name of [child].
     * If there is no such subcommand, it means that [child] is a nested command in a place of argument with [ShellArgument.isCommand] = true.
     */
    fun isSubcommand(parent: SubcommandNode, child: SubcommandNode): Boolean {
      return parent.spec.subcommands.find { subCmd -> child.spec.names.any { subCmd.names.contains(it) } } != null
    }

    var child = node
    var parent = node.parent
    // parent commands can define 'persistent' options - they can be used in all subcommands
    // but add persistent options from parent, only if it is a direct subcommand
    while (parent is SubcommandNode && isSubcommand(parent, child)) {
      options.addAll(parent.spec.options.filter { it.isPersistent })
      child = parent
      parent = parent.parent
    }
    return options
  }

  private suspend fun getSuggestionsForOption(node: OptionNode, nextNodeText: String): List<BaseSuggestion> {
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

  private suspend fun getArgumentSuggestions(arg: ShellArgument, nextNodeText: String): List<BaseSuggestion> {
    val suggestions = mutableListOf<BaseSuggestion>()

    if (arg.isCommand) {
      val commands = getAvailableCommands()
      suggestions.addAll(commands)
    }

    val suggestAllFiles = arg.isFilePath()
    val suggestFolders = arg.isFolder()
    if (suggestAllFiles || suggestFolders) {
      val fileSuggestions = getFileSuggestions(arg, nextNodeText, onlyDirectories = suggestFolders && !suggestAllFiles)
      suggestions.addAll(fileSuggestions)
    }
    if (!suggestAllFiles && !suggestFolders || !nextNodeText.contains(File.separatorChar)) {
      suggestions.addAll(arg.suggestions.map { ShellArgumentSuggestion(it, arg) })
    }
    return suggestions
  }

  suspend fun getFileSuggestions(arg: ShellArgument, nextNodeText: String, onlyDirectories: Boolean): List<ShellArgumentSuggestion> {
    val separator = File.separatorChar
    val basePath = if (nextNodeText.contains(separator)) {
      nextNodeText.substringBeforeLast(separator) + separator
    }
    else "."
    val files = runtimeDataProvider.getFilesFromDirectory(basePath)
    return files.asSequence()
      .filter { !onlyDirectories || it.endsWith(separator) }
      // do not suggest './' and '../' directories if the user already typed some path
      .filter { basePath == "." || (it != ".$separator" && it != "..$separator") }
      .map { ShellArgumentSuggestion(ShellSuggestion(names = listOf(it)), arg) }
      // add an empty choice to be able to handle the case when the folder is chosen
      .let { if (basePath != ".") it.plus(ShellArgumentSuggestion(ShellSuggestion(names = listOf("")), arg)) else it }
      .toList()
  }
}