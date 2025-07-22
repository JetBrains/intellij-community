// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.terminal.completion.ShellCommandSpecCompletion
import com.intellij.terminal.completion.ShellDataGeneratorsExecutor
import com.intellij.terminal.completion.ShellRuntimeContextProvider
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.completion.spec.ShellSuggestionType
import org.jetbrains.plugins.terminal.LocalBlockTerminalRunner
import org.jetbrains.plugins.terminal.block.completion.ShellCommandSpecsManagerImpl
import org.jetbrains.plugins.terminal.block.completion.TerminalCompletionScope
import org.jetbrains.plugins.terminal.block.completion.TerminalCompletionUtil
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellDataGeneratorsExecutorReworkedImpl
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellMergedCommandSpec
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellRuntimeContextProviderReworkedImpl
import org.jetbrains.plugins.terminal.block.reworked.TerminalAliasesStorage
import org.jetbrains.plugins.terminal.block.reworked.TerminalBlocksModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isSuppressCompletion
import org.jetbrains.plugins.terminal.exp.completion.TerminalShellSupport
import org.jetbrains.plugins.terminal.util.ShellType
import java.io.File

internal class TerminalCommandSpecCompletionContributorGen2 : CompletionContributor(), DumbAware {
  val tracer = TelemetryManager.getTracer(TerminalCompletionScope)

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (!parameters.editor.isReworkedTerminalEditor) return
    val sessionModel = parameters.editor.getUserData(TerminalSessionModel.KEY) ?: return
    val runtimeContextProvider = ShellRuntimeContextProviderReworkedImpl(parameters.editor.project!!, sessionModel)
    val generatorsExecutor = ShellDataGeneratorsExecutorReworkedImpl()
    val blocksModel = parameters.editor.getUserData(TerminalBlocksModel.KEY) ?: return
    val lastBlock = blocksModel.blocks.lastOrNull() ?: return

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
    val context = TerminalCompletionContext(runtimeContextProvider, generatorsExecutor, shellSupport, parameters, ShellType.ZSH)

    val document = parameters.editor.document
    val caretOffset = parameters.editor.caretModel.offset
    val command = document.getText(TextRange.create(lastBlock.commandStartOffset, caretOffset))
    val tokens = shellSupport.getCommandTokens(parameters.editor.project!!, command) ?: return
    val allTokens = if (caretOffset != 0 && document.getText(TextRange.create(caretOffset - 1, caretOffset)) == " ") {
      tokens + ""  // user inserted space after the last token, so add empty incomplete token as last
    }
    else {
      tokens
    }
    val aliasesStorage = parameters.editor.getUserData(TerminalAliasesStorage.KEY)

    if (allTokens.isEmpty()) {
      return
    }
    tracer.spanBuilder("terminal-completion-all").use {
      val suggestions = runBlockingCancellable {
        val expandedTokens = expandAliases(context, allTokens, aliasesStorage)
        computeSuggestions(expandedTokens, context, parameters.isAutoPopup)
      }
      tracer.spanBuilder("terminal-completion-submit-suggestions-to-lookup").use {
        submitSuggestions(suggestions, allTokens, result, ShellType.ZSH, parameters.isAutoPopup)
      }
    }
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

    // A partial pop-up implementation is required, as users find automatic pop-ups intrusive.
    // This approach makes the pop-up discoverable while being less disruptive.
    // The pop-up is triggered only when entering a command's argument
    // (e.g., file after `ls`, folder after `cd`, branch after `git checkout`).
    if (isAutoPopup) {
      val containsShellCommand = suggestions.any { it.type == ShellSuggestionType.COMMAND }
      if (containsShellCommand) {
        return
      }
    }

    val elements = suggestions.map { it.toLookupElement(shellType) }
    resultSet.addAllElements(elements)

    if (elements.isNotEmpty()) {
      resultSet.stopHere()
    }
  }

  private suspend fun computeSuggestions(tokens: List<String>, context: TerminalCompletionContext, isAutoPopup: Boolean): List<ShellCompletionSuggestion> {
    if (tokens.isEmpty()) {
      return emptyList()
    }

    val runtimeContext = context.runtimeContextProvider.getContext(tokens.last())
    val completion = ShellCommandSpecCompletion(ShellCommandSpecsManagerImpl.getInstance(), context.generatorsExecutor,
                                                context.runtimeContextProvider)
    val commandExecutable = tokens.first()
    val commandArguments = tokens.subList(1, tokens.size)

    val fileProducer = suspend { context.generatorsExecutor.execute(runtimeContext, ShellDataGenerators.fileSuggestionsGenerator()) }
    val specCompletionFunction: suspend (String) -> List<ShellCompletionSuggestion>? = { commandName ->
      tracer.spanBuilder("terminal-completion-compute-completion-items").useWithScope {
        completion.computeCompletionItems(commandName, commandArguments)
      }
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

    // Remove path separator from insert value, so there will be an exact match
    // if the prefix is the same string, but without path separator.
    // It is needed, for example, to place the './' item in the first place when '.' is typed.
    // It is a hack, because generally this logic should be solved by overriding LookupArranger#isPrefixItem.
    // But there is no API to substitute our own implementation of LookupArranger.
    val (lookupString, appendPathSeparator) = if (escapedInsertValue.endsWith(File.separatorChar)) {
      escapedInsertValue.removeSuffix(File.separator) to true
    }
    else {
      escapedInsertValue to false
    }

    val element = LookupElementBuilder.create(this, lookupString)
      .withPresentableText(displayName ?: name)
      .withTailText(nextSuggestions, true)
      .withIcon(actualIcon)
      .withInsertHandler(MyInsertHandler(this, appendPathSeparator, shellType))

    val adjustedPriority = priority.coerceIn(0, 100)
    return PrioritizedLookupElement.withPriority(element, adjustedPriority / 100.0)
  }

  private class MyInsertHandler(
    private val suggestion: ShellCompletionSuggestion,
    private val appendPathSeparator: Boolean,
    private val shellType: ShellType,
  ) : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
      // PowerShell consider both slash and backslash as valid path separators.
      // But after suggestion insertion, it is replacing wrong path separators with OS path separators.
      // Here we are emulating the same behavior.
      if (shellType == ShellType.POWERSHELL && (suggestion.type == ShellSuggestionType.FOLDER || suggestion.type == ShellSuggestionType.FILE)) {
        val pathStartOffset = context.startOffset - suggestion.prefixReplacementIndex
        val pathText = context.document.immutableCharSequence.substring(pathStartOffset, context.tailOffset)
        val wrongSeparator = if (File.separatorChar == '/') '\\' else '/'
        val adjustedPathText = pathText.replace(wrongSeparator, File.separatorChar)
        context.document.replaceString(pathStartOffset, context.tailOffset, adjustedPathText)
      }
      if (appendPathSeparator) {
        val tailOffset = context.tailOffset
        context.document.insertString(tailOffset, File.separator)
        context.editor.caretModel.moveToOffset(tailOffset + 1)
      }
      val cursorOffset = suggestion.insertValue?.indexOf("{cursor}")
      if (cursorOffset != null && cursorOffset != -1) {
        context.editor.caretModel.moveToOffset(context.startOffset + cursorOffset)
      }
    }
  }

  private class TerminalCompletionContext(
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