package com.intellij.terminal.completion

import com.intellij.terminal.completion.engine.ShellCommandNode
import com.intellij.terminal.completion.engine.ShellCommandTreeBuilder
import com.intellij.terminal.completion.engine.ShellCommandTreeNode
import com.intellij.terminal.completion.engine.ShellCommandTreeSuggestionsProvider
import com.intellij.terminal.completion.spec.ShellCommandSpec
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.util.containers.TreeTraversal
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ShellCommandSpecCompletion(
  private val commandSpecManager: ShellCommandSpecsManager,
  private val generatorsExecutor: ShellDataGeneratorsExecutor,
  private val contextProvider: ShellRuntimeContextProvider
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
  suspend fun computeCompletionItems(command: String, arguments: List<String>): List<ShellCompletionSuggestion>? {
    if (arguments.isEmpty()) {
      return null  // no arguments, there should be at least one empty incomplete argument
    }
    val commandSpec: ShellCommandSpec = commandSpecManager.getCommandSpec(command) ?: return null  // no spec for command

    val completeArguments = arguments.subList(0, arguments.size - 1)
    val lastArgument = arguments.last()
    val rootNode: ShellCommandNode = ShellCommandTreeBuilder.build(contextProvider, generatorsExecutor, commandSpecManager,
                                                                   command, commandSpec, completeArguments)
    val context = contextProvider.getContext(lastArgument)
    val suggestionsProvider = ShellCommandTreeSuggestionsProvider(context, generatorsExecutor)
    return computeSuggestions(suggestionsProvider, rootNode)
  }

  private suspend fun computeSuggestions(
    suggestionsProvider: ShellCommandTreeSuggestionsProvider,
    root: ShellCommandNode
  ): List<ShellCompletionSuggestion> {
    val allChildren = TreeTraversal.PRE_ORDER_DFS.traversal(root as ShellCommandTreeNode<*>) { node -> node.children }
    val lastNode = allChildren.last() ?: root
    return suggestionsProvider.getSuggestionsOfNext(lastNode).filter { !it.isHidden }
  }
}
