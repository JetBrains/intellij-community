package com.intellij.terminal.frontend.view.completion

import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import org.jetbrains.plugins.terminal.block.completion.TerminalCompletionUtil
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators
import org.jetbrains.plugins.terminal.block.completion.spec.impl.TerminalCommandCompletionServices

internal class TerminalFilesCompletionContributor : TerminalCommandCompletionContributor {
  override suspend fun getCompletionSuggestions(context: TerminalCommandCompletionContext): TerminalCommandCompletionResult? {
    if (context.isAutoPopup) {
      return null
    }

    val commandTokens = getCommandTokens(context)
    if (commandTokens.isEmpty()) {
      return null
    }

    val prefix = TerminalCompletionUtil.getTypedPrefix(commandTokens)
    val completionServices = context.editor.getUserData(TerminalCommandCompletionServices.KEY) ?: return null
    val suggestions = computeFileSuggestions(commandTokens, completionServices)
    return TerminalCommandCompletionResult(suggestions, prefix)
  }

  private suspend fun computeFileSuggestions(
    tokens: List<String>,
    completionServices: TerminalCommandCompletionServices,
  ): List<ShellCompletionSuggestion> {
    val runtimeContext = completionServices.runtimeContextProvider.getContext(tokens)
    val generatorsExecutor = completionServices.dataGeneratorsExecutor
    val suggestions = generatorsExecutor.execute(runtimeContext, ShellDataGenerators.fileSuggestionsGenerator())
                      ?: emptyList()
    return suggestions.filter { !it.isHidden }
  }
}