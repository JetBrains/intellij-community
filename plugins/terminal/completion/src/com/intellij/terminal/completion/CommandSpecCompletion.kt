package com.intellij.terminal.completion

import com.intellij.util.containers.TreeTraversal
import org.jetbrains.terminal.completion.BaseSuggestion
import org.jetbrains.terminal.completion.ShellArgument

class CommandSpecCompletion(
  private val commandSpecManager: CommandSpecManager,
  private val runtimeDataProvider: ShellRuntimeDataProvider
) {

  /**
   * @param [commandTokens] parts of the command. Note that all tokens except of last are considered as complete.
   *  The completions are computed for the last token, so it should be explicitly specified as "", if the prefix is empty.
   * 1. Chained option (like '-ald') should be a single token.
   * 2. Option with separator (like '--opt=abc') should be a single token.
   * 3. File path should be a single token.
   * 4. Quoted string should be a single token.
   *
   * @return null if command name is empty or failed to find the command spec for command.
   */
  suspend fun computeCompletionItems(commandTokens: List<String>): List<BaseSuggestion>? {
    if (commandTokens.isEmpty() || commandTokens.singleOrNull()?.isBlank() == true) {
      return null  // do not propose command suggestions if there is an empty command prefix
    }
    if (commandTokens.size == 1) {
      // command name is incomplete, so provide suggestions for commands and files
      return computeCommandsAndFiles(commandTokens)
    }
    val command = commandTokens.first()
    val arguments = commandTokens.subList(1, commandTokens.size)

    val commandSpec = commandSpecManager.getCommandSpec(command) ?: return null  // no spec for command

    val completeArguments = arguments.subList(0, arguments.size - 1)
    val lastArgument = arguments.last()
    val suggestionsProvider = CommandTreeSuggestionsProvider(commandSpecManager, runtimeDataProvider)
    val rootNode: SubcommandNode = CommandTreeBuilder.build(suggestionsProvider, commandSpecManager,
                                                            command, commandSpec, completeArguments)
    return computeSuggestions(suggestionsProvider, rootNode, lastArgument)
  }

  private suspend fun computeCommandsAndFiles(commandTokens: List<String>): List<BaseSuggestion> {
    assert(commandTokens.isNotEmpty())
    val files = computeFileItems(commandTokens) ?: emptyList()
    return if (commandTokens.last().contains("/")) {
      files  // cur token contains path delimiter, so it is a path, and we should not propose commands
    }
    else {
      val suggestionsProvider = CommandTreeSuggestionsProvider(commandSpecManager, runtimeDataProvider)
      val commands = suggestionsProvider.getAvailableCommands()
      files + commands
    }
  }

  /**
   * Returns the file suggestions for the last argument from [commandTokens] if this list is not empty.
   */
  suspend fun computeFileItems(commandTokens: List<String>): List<BaseSuggestion>? {
    if (commandTokens.isEmpty() || commandTokens.singleOrNull()?.isBlank() == true) {
      return null
    }
    val suggestionsProvider = CommandTreeSuggestionsProvider(commandSpecManager, runtimeDataProvider)
    val fakeArgument = ShellArgument(templates = listOf("filepaths"))
    val lastArgument = commandTokens.last()
    return suggestionsProvider.getFileSuggestions(fakeArgument, lastArgument, onlyDirectories = false).filterEmptyNames()
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