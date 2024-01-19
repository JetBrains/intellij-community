package com.intellij.terminal.completion

import com.intellij.util.containers.TreeTraversal
import org.jetbrains.terminal.completion.BaseSuggestion
import org.jetbrains.terminal.completion.ShellArgument

class CommandSpecCompletion(
  private val commandSpecManager: CommandSpecManager,
  private val runtimeDataProvider: ShellRuntimeDataProvider
) {

  /**
   * @param [arguments] parts of the command. Note that all arguments except of last are considered as complete.
   *  The completions are computed for the last argument, so it should be explicitly specified as "", if the prefix is empty.
   * 1. Chained option (like '-ald') should be a single token.
   * 2. Option with separator (like '--opt=abc') should be a single token.
   * 3. File path should be a single token.
   * 4. Quoted string should be a single token.
   *
   * @return null if not enough arguments or failed to find the command spec for command.
   */
  suspend fun computeCompletionItems(command: String, arguments: List<String>): List<BaseSuggestion>? {
    if (arguments.isEmpty()) {
      return null  // no arguments, there should be at least one empty incomplete argument
    }
    val commandSpec = commandSpecManager.getCommandSpec(command) ?: return null  // no spec for command

    val completeArguments = arguments.subList(0, arguments.size - 1)
    val lastArgument = arguments.last()
    val suggestionsProvider = CommandTreeSuggestionsProvider(commandSpecManager, runtimeDataProvider)
    val rootNode: SubcommandNode = CommandTreeBuilder.build(suggestionsProvider, commandSpecManager,
                                                            command, commandSpec, completeArguments)
    return computeSuggestions(suggestionsProvider, rootNode, lastArgument)
  }

  suspend fun computeCommandsAndFiles(incompleteToken: String): List<BaseSuggestion> {
    if (incompleteToken.isBlank()) return emptyList()
    val files = computeFileItems(incompleteToken)
    return if (incompleteToken.contains("/")) {
      files  // cur token contains path delimiter, so it is a path, and we should not propose commands
    }
    else {
      val suggestionsProvider = CommandTreeSuggestionsProvider(commandSpecManager, runtimeDataProvider)
      val commands = suggestionsProvider.getAvailableCommands()
      files + commands
    }
  }

  /**
   * Returns the file suggestions for the provided [incompletePath]
   */
  suspend fun computeFileItems(incompletePath: String): List<BaseSuggestion> {
    if (incompletePath.isBlank()) return emptyList()
    val suggestionsProvider = CommandTreeSuggestionsProvider(commandSpecManager, runtimeDataProvider)
    val fakeArgument = ShellArgument(templates = listOf("filepaths"))
    return suggestionsProvider.getFileSuggestions(fakeArgument, incompletePath, onlyDirectories = false).filterEmptyNames()
  }

  private suspend fun computeSuggestions(suggestionsProvider: CommandTreeSuggestionsProvider,
                                         root: SubcommandNode,
                                         lastArgument: String): List<BaseSuggestion> {
    val allChildren = TreeTraversal.PRE_ORDER_DFS.traversal(root as CommandPartNode<*>) { node -> node.children }
    val lastNode = allChildren.last() ?: root
    return suggestionsProvider.getSuggestionsOfNext(lastNode, lastArgument).filterEmptyNames()
  }

  private fun Iterable<BaseSuggestion>.filterEmptyNames(): List<BaseSuggestion> {
    return filter { s -> s.names.all { it.isNotEmpty() } }
  }
}