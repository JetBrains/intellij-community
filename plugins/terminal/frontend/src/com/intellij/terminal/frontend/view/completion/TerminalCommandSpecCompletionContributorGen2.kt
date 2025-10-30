// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.terminal.completion.ShellCommandSpecCompletion
import com.intellij.terminal.completion.ShellCommandSpecsManager
import com.intellij.terminal.completion.ShellDataGeneratorsExecutor
import com.intellij.terminal.completion.ShellRuntimeContextProvider
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.completion.spec.ShellSuggestionType
import org.jetbrains.plugins.terminal.LocalBlockTerminalRunner
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.block.completion.TerminalCommandCompletionShowingMode
import org.jetbrains.plugins.terminal.block.completion.TerminalCompletionUtil
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators
import org.jetbrains.plugins.terminal.block.completion.spec.impl.TerminalCommandCompletionServices
import org.jetbrains.plugins.terminal.block.reworked.TerminalAliasesStorage
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isSuppressCompletion
import org.jetbrains.plugins.terminal.exp.completion.TerminalShellSupport
import org.jetbrains.plugins.terminal.util.ShellType
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalBlocksModel
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandBlock

internal class TerminalCommandSpecCompletionContributorGen2 : CompletionContributor(), DumbAware {

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (!parameters.editor.isReworkedTerminalEditor) return
    val outputModel = parameters.editor.getUserData(TerminalOutputModel.KEY) ?: return
    val blocksModel = parameters.editor.getUserData(TerminalBlocksModel.KEY) ?: return
    val commandBlock = blocksModel.activeBlock as? TerminalCommandBlock ?: return
    val commandStartOffset = commandBlock.commandStartOffset ?: return

    if (parameters.completionType != CompletionType.BASIC) {
      return
    }

    if (parameters.isAutoPopup && !Registry.`is`(LocalBlockTerminalRunner.BLOCK_TERMINAL_AUTOCOMPLETION)) {
      result.stopHere()
      return
    }

    if (parameters.editor.isSuppressCompletion) {
      result.stopHere()
      return
    }

    val shellSupport = TerminalShellSupport.findByShellType(ShellType.ZSH) ?: return
    val completionServices = parameters.editor.getUserData(TerminalCommandCompletionServices.KEY) ?: return
    val context = TerminalCompletionContext(
      completionServices.commandSpecsManager,
      completionServices.runtimeContextProvider,
      completionServices.dataGeneratorsExecutor,
      shellSupport,
      parameters,
      ShellType.ZSH
    )

    val document = parameters.editor.document
    val caretOffset = parameters.editor.caretModel.offset
    val command = outputModel.getText(commandStartOffset, outputModel.startOffset + caretOffset.toLong()).toString()
    val tokens = shellSupport.getCommandTokens(parameters.editor.project!!, command) ?: return
    val allTokens = if (caretOffset != 0 && document.getText(TextRange.create(caretOffset - 1, caretOffset)) == " ") {
      tokens + ""  // user inserted space after the last token, so add empty incomplete token as last
    }
    else {
      tokens
    }

    if (allTokens.isEmpty()) {
      return
    }

    val prefix = allTokens.last()
    if (parameters.isAutoPopup && prefix.startsWith("-") && prefix.length <= 2) {
      // Do not show the completion popup automatically for short options like `-a` or `-h`
      // Most probably, it will cause only distraction.
      return
    }

    val suggestions = runBlockingCancellable {
      val aliasesStorage = parameters.editor.getUserData(TerminalAliasesStorage.KEY)
      val expandedTokens = expandAliases(context, allTokens, aliasesStorage)
      computeSuggestions(expandedTokens, context, parameters.isAutoPopup)
    }
    submitSuggestions(suggestions, allTokens, result, ShellType.ZSH, parameters.isAutoPopup)
  }

  private fun submitSuggestions(
    suggestions: List<ShellCompletionSuggestion>,
    allTokens: List<String>,
    result: CompletionResultSet,
    shellType: ShellType,
    isAutoPopup: Boolean,
  ) {
    val prefixReplacementIndex = suggestions.firstOrNull()?.prefixReplacementIndex ?: 0
    val prefix = allTokens.last().substring(prefixReplacementIndex)
    val resultSet = result.withPrefixMatcher(PlainPrefixMatcher(prefix, true))

    // todo: need to find a better place for checking it
    //  because determining the context and checking the prefix can be done before computing the completion suggestions.
    if (isAutoPopup && TerminalOptionsProvider.instance.commandCompletionShowingMode == TerminalCommandCompletionShowingMode.ONLY_PARAMETERS) {
      // If ONLY_PARAMETERS mode is specified, show the completion popup only in specific contexts:
      // when completing command options and arguments (for example, files after `ls`, branches after `git checkout`).
      // It will make the completion popup less intrusive.
      if (!isSuggestingParameters(suggestions)) {
        return
      }
    }

    val elements = suggestions.map { it.toLookupElement(shellType) }
    resultSet.addAllElements(elements)

    if (elements.isNotEmpty()) {
      resultSet.stopHere()
    }
  }

  private fun isSuggestingParameters(suggestions: List<ShellCompletionSuggestion>): Boolean {
    // Show the popup only if there are no suggestions for subcommands (only options and arguments).
    return suggestions.none { it.type == ShellSuggestionType.COMMAND }
  }

  private suspend fun computeSuggestions(tokens: List<String>, context: TerminalCompletionContext, isAutoPopup: Boolean): List<ShellCompletionSuggestion> {
    if (tokens.isEmpty()) {
      return emptyList()
    }

    val runtimeContext = context.runtimeContextProvider.getContext(tokens.last())
    val completion = ShellCommandSpecCompletion(
      context.commandSpecsManager,
      context.generatorsExecutor,
      context.runtimeContextProvider
    )
    val commandExecutable = tokens.first()
    val commandArguments = tokens.subList(1, tokens.size)

    val fileProducer = suspend { context.generatorsExecutor.execute(runtimeContext, ShellDataGenerators.fileSuggestionsGenerator()) }
    val specCompletionFunction: suspend (String) -> List<ShellCompletionSuggestion>? = { commandName ->
      completion.computeCompletionItems(commandName, commandArguments)
    }

    if (commandArguments.isEmpty()) {
      if (context.shellType == ShellType.POWERSHELL || isAutoPopup) {
        // Return no completions for command name to pass the completion to the PowerShell
        return emptyList()
      }
      val suggestions = fileProducer()
      return suggestions.filter { !it.isHidden }
    }
    else {
      return computeSuggestionsIfHasArguments(commandExecutable, context, fileProducer, specCompletionFunction)
    }
  }

  private suspend fun computeSuggestionsIfHasArguments(
    commandExecutable: String,
    context: TerminalCompletionContext,
    fileProducer: suspend () -> List<ShellCompletionSuggestion>,
    specCompletionFunction: suspend (String) -> List<ShellCompletionSuggestion>?,
  ): List<ShellCompletionSuggestion> {

    val commandVariants = getCommandNameVariants(commandExecutable)
    val items = commandVariants.firstNotNullOfOrNull { specCompletionFunction(it) } ?: emptyList()
    if (items.isNotEmpty()) {
      return items
    }

    if (context.parameters.isAutoPopup) {
      return emptyList()
    }

    // Fall back to shell-based completion if it is PowerShell. It might provide more specific suggestions than just files.
    if (context.shellType == ShellType.POWERSHELL) {
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

  private fun expandAliases(
    context: TerminalCompletionContext,
    tokens: List<String>,
    aliasesStorage: TerminalAliasesStorage?,
  ): List<String> {
    if (tokens.size < 2) {
      return tokens
    }
    if (aliasesStorage != null) {
      val expandedTokens = expandAliases(tokens, aliasesStorage.getAliasesInfo().aliases, context)
      return expandedTokens
    }
    return tokens
  }

  /**
   * Used to support completion even if the real command is hidden behind the alias.
   */
  private fun expandAliases(tokens: List<String>, aliases: Map<String, String>, context: TerminalCompletionContext): List<String> {
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
    val expandedTokens = context.shellSupport.getCommandTokens(context.project, command.toString())
    return (expandedTokens ?: completeTokens) + tokens.last() // add incomplete token to the end
  }

  private fun ShellCompletionSuggestion.toLookupElement(shellType: ShellType): LookupElement {
    val actualIcon = icon ?: TerminalCompletionUtil.findIconForSuggestion(name, type)
    val realInsertValue = insertValue?.replace("{cursor}", "")
    val nextSuggestions = TerminalCompletionUtil.getNextSuggestionsString(this).takeIf { it.isNotEmpty() }
    val escapedInsertValue = StringUtil.escapeChar(realInsertValue ?: name, ' ')

    val element = LookupElementBuilder.create(this, escapedInsertValue)
      .withPresentableText(displayName ?: name)
      .withTailText(nextSuggestions, true)
      .withIcon(TerminalStatefulDelegatingIcon(actualIcon))
    // Actual insertion logic is performed in TerminalLookupListener
    element.putUserData(CodeCompletionHandlerBase.DIRECT_INSERTION, true)

    val adjustedPriority = priority.coerceIn(0, 100)
    return PrioritizedLookupElement.withPriority(element, adjustedPriority / 100.0)
  }

  private class TerminalCompletionContext(
    val commandSpecsManager: ShellCommandSpecsManager,
    val runtimeContextProvider: ShellRuntimeContextProvider,
    val generatorsExecutor: ShellDataGeneratorsExecutor,
    val shellSupport: TerminalShellSupport,
    val parameters: CompletionParameters,
    val shellType: ShellType,
  ) {
    val project: Project
      get() = parameters.editor.project!!
  }

}