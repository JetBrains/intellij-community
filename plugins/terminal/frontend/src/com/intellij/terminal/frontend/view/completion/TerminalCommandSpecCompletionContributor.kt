package com.intellij.terminal.frontend.view.completion

import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.terminal.completion.ShellCommandSpecCompletion
import com.intellij.terminal.completion.ShellCommandSpecsManager
import com.intellij.terminal.completion.ShellDataGeneratorsExecutor
import com.intellij.terminal.completion.ShellRuntimeContextProvider
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators
import org.jetbrains.plugins.terminal.block.completion.spec.impl.TerminalCommandCompletionServices
import org.jetbrains.plugins.terminal.exp.completion.TerminalShellSupport
import org.jetbrains.plugins.terminal.util.ShellType

internal class TerminalCommandSpecCompletionContributor : TerminalCommandCompletionContributor {
  override suspend fun getCompletionSuggestions(context: TerminalCommandCompletionContext): TerminalCommandCompletionResult? {
    val shellSupport = TerminalShellSupport.findByShellType(ShellType.ZSH) ?: return null

    val localCursorOffset = context.initialCursorOffset - context.commandStartOffset
    val commandText = context.commandText.substring(0, localCursorOffset.toInt()).trimStart()
    val tokens = readAction {
      shellSupport.getCommandTokens(context.project, commandText)
    } ?: return null
    val allTokens = if (commandText.endsWith(' ') && commandText.isNotBlank()) {
      tokens + ""  // user inserted space after the last token, so add an empty incomplete token as last
    }
    else {
      tokens
    }

    if (allTokens.isEmpty()) {
      return null
    }

    val prefix = allTokens.last()
    if (context.isAutoPopup && prefix.startsWith("-") && prefix.length <= 2) {
      // Do not show the completion popup automatically for short options like `-a` or `-h`
      // Most probably, it will cause only distraction.
      return null
    }

    val completionServices = context.editor.getUserData(TerminalCommandCompletionServices.KEY) ?: return null
    val parameters = TerminalCompletionParameters(
      context.project,
      completionServices.commandSpecsManager,
      completionServices.runtimeContextProvider,
      completionServices.dataGeneratorsExecutor,
      shellSupport,
      ShellType.ZSH,
      context.isAutoPopup
    )

    val aliasesMap = context.shellIntegration.commandAliases
    val expandedTokens = expandAliases(parameters, allTokens, aliasesMap)
    val suggestions = computeSuggestions(expandedTokens, parameters, parameters.isAutoPopup)
    return TerminalCommandCompletionResult(suggestions, prefix)
  }

  private suspend fun expandAliases(
    parameters: TerminalCompletionParameters,
    tokens: List<String>,
    aliasesMap: Map<String, String>,
  ): List<String> {
    if (tokens.size < 2) {
      return tokens
    }
    if (aliasesMap.isNotEmpty()) {
      val expandedTokens = expandAliases(tokens, aliasesMap, parameters)
      return expandedTokens
    }
    return tokens
  }

  /**
   * Used to support completion even if the real command is hidden behind the alias.
   */
  private suspend fun expandAliases(
    tokens: List<String>,
    aliases: Map<String, String>,
    parameters: TerminalCompletionParameters,
  ): List<String> {
    // If there is only one token, we should not to expand it, because it is incomplete (e.g. `gi|`)
    if (aliases.isEmpty() || tokens.size < 2) {
      return tokens
    }
    // do not expand last token, because it can be incomplete
    val completeTokens = tokens.subList(0, tokens.size - 1)
    val command = StringBuilder()
    var anyAliasFound = false
    for (token in completeTokens) {
      val aliasedCommand = aliases[token]
      if (aliasedCommand != null) {
        anyAliasFound = true
      }
      command.append(aliasedCommand ?: token)
      command.append(' ')
    }
    if (!anyAliasFound) {
      return tokens  // command is not changed, so return initial tokens
    }
    val expandedTokens = readAction {
      parameters.shellSupport.getCommandTokens(parameters.project, command.toString())
    }
    return (expandedTokens ?: completeTokens) + tokens.last() // add incomplete token to the end
  }

  private suspend fun computeSuggestions(
    tokens: List<String>,
    parameters: TerminalCompletionParameters,
    isAutoPopup: Boolean,
  ): List<ShellCompletionSuggestion> {
    if (tokens.isEmpty()) {
      return emptyList()
    }

    val runtimeContext = parameters.runtimeContextProvider.getContext(tokens)
    val completion = ShellCommandSpecCompletion(
      parameters.commandSpecsManager,
      parameters.generatorsExecutor,
      parameters.runtimeContextProvider
    )
    val commandExecutable = tokens.first()
    val commandArguments = tokens.subList(1, tokens.size)

    val fileProducer = suspend {
      parameters.generatorsExecutor.execute(runtimeContext, ShellDataGenerators.fileSuggestionsGenerator())
      ?: emptyList()
    }
    val specCompletionFunction: suspend (String) -> List<ShellCompletionSuggestion>? = { commandName ->
      completion.computeCompletionItems(commandName, commandArguments)
    }

    if (commandArguments.isEmpty()) {
      if (parameters.shellType == ShellType.POWERSHELL || isAutoPopup) {
        // Return no completions for command name to pass the completion to the PowerShell
        return emptyList()
      }
      val suggestions = fileProducer()
      return suggestions.filter { !it.isHidden }
    }
    else {
      return computeSuggestionsIfHasArguments(commandExecutable, parameters, fileProducer, specCompletionFunction)
    }
  }

  private suspend fun computeSuggestionsIfHasArguments(
    commandExecutable: String,
    parameters: TerminalCompletionParameters,
    fileProducer: suspend () -> List<ShellCompletionSuggestion>,
    specCompletionFunction: suspend (String) -> List<ShellCompletionSuggestion>?,
  ): List<ShellCompletionSuggestion> {

    val commandVariants = getCommandNameVariants(commandExecutable)
    val items = commandVariants.firstNotNullOfOrNull { specCompletionFunction(it) } ?: emptyList()
    if (items.isNotEmpty()) {
      return items
    }

    if (parameters.isAutoPopup) {
      return emptyList()
    }

    // Fall back to shell-based completion if it is PowerShell. It might provide more specific suggestions than just files.
    if (parameters.shellType == ShellType.POWERSHELL) {
      return emptyList()
    }

    // Suggest file names if there is nothing to suggest, and completion is invoked manually.
    return fileProducer().filter { !it.isHidden }
  }

  private fun getCommandNameVariants(commandExecutable: String): List<String> {
    val result = mutableListOf(commandExecutable)
    if (commandExecutable.endsWith(".exe")) {
      result += commandExecutable.removeSuffix(".exe")
    }
    return result
  }

  private class TerminalCompletionParameters(
    val project: Project,
    val commandSpecsManager: ShellCommandSpecsManager,
    val runtimeContextProvider: ShellRuntimeContextProvider,
    val generatorsExecutor: ShellDataGeneratorsExecutor,
    val shellSupport: TerminalShellSupport,
    val shellType: ShellType,
    val isAutoPopup: Boolean,
  )
}