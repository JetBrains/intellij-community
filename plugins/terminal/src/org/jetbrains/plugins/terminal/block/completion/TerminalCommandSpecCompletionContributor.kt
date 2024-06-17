// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.terminal.completion.ShellCommandSpecCompletion
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.completion.spec.ShellSuggestionType
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

internal class TerminalCommandSpecCompletionContributor : CompletionContributor(), DumbAware {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val session = parameters.editor.getUserData(BlockTerminalSession.KEY)
    val runtimeContextProvider = parameters.editor.getUserData(ShellRuntimeContextProviderImpl.KEY)
    val generatorsExecutor = parameters.editor.getUserData(ShellDataGeneratorsExecutorImpl.KEY)
    val promptModel = parameters.editor.terminalPromptModel
    if (session == null ||
        session.model.isCommandRunning ||
        runtimeContextProvider == null ||
        generatorsExecutor == null ||
        promptModel == null ||
        parameters.completionType != CompletionType.BASIC) {
      return
    }
    if (parameters.editor.getUserData(TerminalCommandCompletionAction.SUPPRESS_COMPLETION) == true) {
      result.stopHere()
      return
    }

    val shellSupport = TerminalShellSupport.findByShellType(session.shellIntegration.shellType) ?: return
    val context = TerminalCompletionContext(session, runtimeContextProvider, generatorsExecutor, shellSupport, parameters)

    val document = parameters.editor.document
    val caretOffset = parameters.editor.caretModel.offset
    val command = document.getText(TextRange.create(promptModel.commandStartOffset, caretOffset))
    val tokens = shellSupport.getCommandTokens(parameters.editor.project!!, command) ?: return
    val allTokens = if (caretOffset != 0 && document.getText(TextRange.create(caretOffset - 1, caretOffset)) == " ") {
      tokens + ""  // user inserted space after the last token, so add empty incomplete token as last
    }
    else tokens
    if (allTokens.isEmpty()) {
      return
    }

    val suggestions = runBlockingCancellable {
      computeSuggestions(allTokens, context)
    }

    val prefixReplacementIndex = suggestions.firstOrNull()?.prefixReplacementIndex ?: 0
    val prefix = allTokens.last().substring(prefixReplacementIndex)
    val resultSet = result.withPrefixMatcher(PlainPrefixMatcher(prefix, true))

    val elements = suggestions.map { it.toLookupElement(session.shellIntegration.shellType) }
    resultSet.addAllElements(elements)

    if (elements.isNotEmpty()) {
      resultSet.stopHere()
    }
  }

  private suspend fun computeSuggestions(tokens: List<String>, context: TerminalCompletionContext): List<ShellCompletionSuggestion> {
    // aliases generator does not requires actual typed prefix
    val dummyRuntimeContext = context.runtimeContextProvider.getContext("")
    val aliases: Map<String, String> = context.generatorsExecutor.execute(dummyRuntimeContext, aliasesGenerator())
    val expandedTokens = expandAliases(tokens, aliases, context)
    if (expandedTokens.isEmpty()) {
      return emptyList()
    }

    val runtimeContext = context.runtimeContextProvider.getContext(expandedTokens.last())
    val completion = ShellCommandSpecCompletion(ShellCommandSpecsManagerImpl.getInstance(), context.generatorsExecutor, context.runtimeContextProvider)
    val command = expandedTokens.first()
    val arguments = expandedTokens.subList(1, expandedTokens.size)
    if (arguments.isEmpty()) {
      val files = context.generatorsExecutor.execute(runtimeContext, fileSuggestionsGenerator())
      val suggestions = if (files.firstOrNull()?.prefixReplacementIndex != 0) {
        files  // Return only files if some file path prefix is already typed
      }
      else {
        val commands = context.generatorsExecutor.execute(runtimeContext, availableCommandsGenerator())
        commands + files
      }
      return suggestions.filter { !it.isHidden }
    }
    else {
      val commandVariants = if (command.endsWith(".exe")) listOf(command.removeSuffix(".exe"), command) else listOf(command)
      val items = commandVariants.firstNotNullOfOrNull { completion.computeCompletionItems(it, arguments) } ?: emptyList()
      return when {
        items.isNotEmpty() -> items
        // Suggest file names if there is nothing to suggest, and completion is invoked manually.
        // But not for PowerShell, here it would be better to fall back to shell-based completion
        !context.parameters.isAutoPopup && context.session.shellIntegration.shellType != ShellType.POWERSHELL -> {
          context.generatorsExecutor.execute(runtimeContext, fileSuggestionsGenerator()).filter { !it.isHidden }
        }
        else -> emptyList()
      }
    }
  }

  private fun expandAliases(tokens: List<String>, aliases: Map<String, String>, context: TerminalCompletionContext): List<String> {
    // If there is only one token, we should not to expand it, because it is incomplete
    if (aliases.isEmpty() || tokens.size < 2) {
      return tokens
    }
    // do not expand last token, because it is incomplete
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
    else escapedInsertValue to false
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
    val session: BlockTerminalSession,
    val runtimeContextProvider: ShellRuntimeContextProviderImpl,
    val generatorsExecutor: ShellDataGeneratorsExecutorImpl,
    val shellSupport: TerminalShellSupport,
    val parameters: CompletionParameters
  ) {
    val project: Project
      get() = parameters.editor.project!!
  }
}
