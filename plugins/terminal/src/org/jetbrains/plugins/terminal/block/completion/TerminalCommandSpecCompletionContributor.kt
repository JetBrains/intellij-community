// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion

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
import org.jetbrains.plugins.terminal.LocalBlockTerminalRunner.Companion.BLOCK_TERMINAL_AUTOCOMPLETION
import org.jetbrains.plugins.terminal.action.TerminalCommandCompletionAction
import org.jetbrains.plugins.terminal.block.completion.TerminalCompletionUtil.findIconForSuggestion
import org.jetbrains.plugins.terminal.block.completion.TerminalCompletionUtil.getNextSuggestionsString
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.availableCommandsGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.fileSuggestionsGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellDataGeneratorsExecutorImpl
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellEnvBasedGenerators.aliasesGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellRuntimeContextProviderImpl
import org.jetbrains.plugins.terminal.block.session.BlockTerminalSession
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalPromptModel
import org.jetbrains.plugins.terminal.exp.completion.TerminalShellSupport
import org.jetbrains.plugins.terminal.util.ShellType
import java.io.File

/**
 * Main entry point to the Block Terminal Completion.
 */
internal class TerminalCommandSpecCompletionContributor : CompletionContributor(), DumbAware {
  val tracer = TelemetryManager.getTracer(TerminalCompletionScope)

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val session = parameters.editor.getUserData(BlockTerminalSession.KEY) ?: return
    val runtimeContextProvider = parameters.editor.getUserData(ShellRuntimeContextProviderImpl.KEY) ?: return
    val generatorsExecutor = parameters.editor.getUserData(ShellDataGeneratorsExecutorImpl.KEY) ?: return
    val promptModel = parameters.editor.terminalPromptModel ?: return

    if (session.model.isCommandRunning || parameters.completionType != CompletionType.BASIC) {
      return
    }

    if (parameters.isAutoPopup && !Registry.`is`(BLOCK_TERMINAL_AUTOCOMPLETION)) {
      result.stopHere()
      return
    }

    if (parameters.editor.getUserData(TerminalCommandCompletionAction.SUPPRESS_COMPLETION) == true) {
      result.stopHere()
      return
    }

    val shellSupport = TerminalShellSupport.findByShellType(session.shellIntegration.shellType) ?: return
    val context = TerminalCompletionContext(runtimeContextProvider, generatorsExecutor, shellSupport, parameters, session.shellIntegration.shellType)

    val document = parameters.editor.document
    val caretOffset = parameters.editor.caretModel.offset
    val command = document.getText(TextRange.create(promptModel.commandStartOffset, caretOffset))
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

    tracer.spanBuilder("terminal-completion-all").use {
      val suggestions = runBlockingCancellable {
        val expandedTokens = expandAliases(context, allTokens)
        computeSuggestions(expandedTokens, context)
      }
      tracer.spanBuilder("terminal-completion-submit-suggestions-to-lookup").use {
        submitSuggestions(suggestions, allTokens, result, session.shellIntegration.shellType)
      }
    }
  }

  private fun submitSuggestions(
    suggestions: List<ShellCompletionSuggestion>,
    allTokens: List<String>,
    result: CompletionResultSet,
    shellType: ShellType,
  ) {
    val prefixReplacementIndex = suggestions.firstOrNull()?.prefixReplacementIndex ?: 0
    val prefix = allTokens.last().substring(prefixReplacementIndex)
    val resultSet = result.withPrefixMatcher(PlainPrefixMatcher(prefix, true))

    val elements = suggestions.map { it.toLookupElement(shellType) }
    resultSet.addAllElements(elements)

    if (elements.isNotEmpty()) {
      resultSet.stopHere()
    }
  }

  private suspend fun computeSuggestions(tokens: List<String>, context: TerminalCompletionContext): List<ShellCompletionSuggestion> {
    if (tokens.isEmpty()) {
      return emptyList()
    }

    val runtimeContext = context.runtimeContextProvider.getContext(tokens.last())
    val completion = ShellCommandSpecCompletion(ShellCommandSpecsManagerImpl.getInstance(), context.generatorsExecutor, context.runtimeContextProvider)
    val commandExecutable = tokens.first()
    val commandArguments = tokens.subList(1, tokens.size)
    val availableCommandsProvider = suspend { context.generatorsExecutor.execute(runtimeContext, availableCommandsGenerator()) }
    val fileProducer = suspend { context.generatorsExecutor.execute(runtimeContext, fileSuggestionsGenerator()) }
    val specCompletionFunction: suspend (String) -> List<ShellCompletionSuggestion>? = { commandName ->
      tracer.spanBuilder("terminal-completion-compute-completion-items").useWithScope {
        completion.computeCompletionItems(commandName, commandArguments)
      }
    }

    if (commandArguments.isEmpty()) {
      if (context.shellType == ShellType.POWERSHELL) {
        // Return no completions for command name to pass the completion to the PowerShell
        return emptyList()
      }
      return computeSuggestionsIfNoArguments(fileProducer, availableCommandsProvider)
    }
    else {
      return computeSuggestionsIfHasArguments(commandExecutable, context, fileProducer, specCompletionFunction)
    }
  }

  private suspend fun computeSuggestionsIfNoArguments(
    fileProducer: suspend () -> List<ShellCompletionSuggestion>,
    availableCommandsProvider: suspend () -> List<ShellCompletionSuggestion>,
  ): List<ShellCompletionSuggestion> {
    val files = fileProducer()
    val suggestions = if (files.firstOrNull()?.prefixReplacementIndex != 0) {
      files  // Return only files if some file path prefix is already typed
    }
    else {
      val commands = availableCommandsProvider()
      commands + files
    }
    return suggestions.filter { !it.isHidden }
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

  private suspend fun expandAliases(
    context: TerminalCompletionContext,
    tokens: List<String>,
  ): List<String> {
    if (tokens.size < 2) {
      return tokens
    }
    // aliases generator does not requires actual typed prefix
    val dummyRuntimeContext = context.runtimeContextProvider.getContext("")
    val aliases: Map<String, String> = context.generatorsExecutor.execute(dummyRuntimeContext, aliasesGenerator())
    val expandedTokens = expandAliases(tokens, aliases, context)
    return expandedTokens
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
    val actualIcon = icon ?: findIconForSuggestion(name, type)
    val realInsertValue = insertValue?.replace("{cursor}", "")
    val nextSuggestions = getNextSuggestionsString(this).takeIf { it.isNotEmpty() }
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
    private val shellType: ShellType
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
    val shellType: ShellType
  ) {
    val project: Project
      get() = parameters.editor.project!!
  }

}
