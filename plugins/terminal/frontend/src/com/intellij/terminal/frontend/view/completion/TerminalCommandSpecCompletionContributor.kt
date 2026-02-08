package com.intellij.terminal.frontend.view.completion

import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.terminal.completion.ShellCommandSpecCompletion
import com.intellij.terminal.completion.ShellCommandSpecsManager
import com.intellij.terminal.completion.ShellDataGeneratorsExecutor
import com.intellij.terminal.completion.ShellRuntimeContextProvider
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import org.jetbrains.plugins.terminal.block.completion.TerminalCompletionUtil
import org.jetbrains.plugins.terminal.block.completion.spec.impl.TerminalCommandCompletionServices
import org.jetbrains.plugins.terminal.exp.completion.TerminalShellSupport
import org.jetbrains.plugins.terminal.util.ShellType

internal class TerminalCommandSpecCompletionContributor : TerminalCommandCompletionContributor {
  override suspend fun getCompletionSuggestions(context: TerminalCommandCompletionContext): TerminalCommandCompletionResult? {
    val shellSupport = TerminalShellSupport.findByShellType(ShellType.ZSH) ?: return null

    val commandTokens = getCommandTokens(context)
    if (commandTokens.isEmpty()) {
      return null
    }

    val prefix = TerminalCompletionUtil.getTypedPrefix(commandTokens)
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
    )

    val aliasesMap = context.shellIntegration.commandAliases
    val expandedTokens = expandAliases(parameters, commandTokens, aliasesMap)
    val suggestions = computeSuggestions(expandedTokens, parameters)
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
    return expandedTokens + tokens.last() // add an incomplete token to the end
  }

  private suspend fun computeSuggestions(
    tokens: List<String>,
    parameters: TerminalCompletionParameters,
  ): List<ShellCompletionSuggestion> {
    if (tokens.isEmpty()) {
      return emptyList()
    }

    val completion = ShellCommandSpecCompletion(
      parameters.commandSpecsManager,
      parameters.generatorsExecutor,
      parameters.runtimeContextProvider
    )
    val commandExecutable = tokens.first()
    val commandArguments = tokens.subList(1, tokens.size)

    val specCompletionFunction: suspend (String) -> List<ShellCompletionSuggestion>? = { commandName ->
      completion.computeCompletionItems(commandName, commandArguments)
    }

    return if (commandArguments.isNotEmpty()) {
      computeSuggestionsIfHasArguments(commandExecutable, specCompletionFunction)
    }
    else emptyList()
  }

  private suspend fun computeSuggestionsIfHasArguments(
    commandExecutable: String,
    specCompletionFunction: suspend (String) -> List<ShellCompletionSuggestion>?,
  ): List<ShellCompletionSuggestion> {
    val commandVariants = getCommandNameVariants(commandExecutable)
    val items = commandVariants.firstNotNullOfOrNull { specCompletionFunction(it) } ?: emptyList()
    return items
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
  )
}

internal suspend fun getCommandTokens(context: TerminalCommandCompletionContext): List<String> {
  val shellSupport = TerminalShellSupport.findByShellType(ShellType.ZSH) ?: return emptyList()

  val localCursorOffset = context.initialCursorOffset - context.commandStartOffset
  val commandText = context.commandText.substring(0, localCursorOffset.toInt()).trimStart()

  val tokens = readAction {
    shellSupport.getCommandTokens(context.project, commandText)
  }

  return if (commandText.endsWith(' ') && commandText.isNotBlank()) {
    tokens + ""  // user inserted space after the last token, so add an empty incomplete token as last
  }
  else {
    tokens
  }
}