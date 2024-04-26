// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.terminal.completion.CommandSpecCompletion
import com.intellij.terminal.completion.ShellRuntimeDataProvider
import org.jetbrains.plugins.terminal.exp.BlockTerminalSession
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.terminalPromptModel
import org.jetbrains.plugins.terminal.exp.completion.TerminalCompletionUtil.findIconForSuggestion
import org.jetbrains.plugins.terminal.exp.completion.TerminalCompletionUtil.getNextSuggestionsString
import org.jetbrains.plugins.terminal.util.ShellType
import org.jetbrains.terminal.completion.BaseSuggestion
import java.io.File

internal class TerminalCommandSpecCompletionContributor : CompletionContributor(), DumbAware {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val session = parameters.editor.getUserData(BlockTerminalSession.KEY)
    val runtimeDataProvider = parameters.editor.getUserData(IJShellRuntimeDataProvider.KEY)
    val promptModel = parameters.editor.terminalPromptModel
    if (session == null ||
        session.model.isCommandRunning ||
        runtimeDataProvider == null || promptModel == null ||
        parameters.completionType != CompletionType.BASIC) {
      return
    }
    val shellSupport = TerminalShellSupport.findByShellType(session.shellIntegration.shellType) ?: return
    val context = TerminalCompletionContext(session, runtimeDataProvider, shellSupport, parameters)

    val prefix = result.prefixMatcher.prefix.substringAfterLast(File.separatorChar) // take last part if it is a file path
    val resultSet = result.withPrefixMatcher(PlainPrefixMatcher(prefix, true))

    val document = parameters.editor.document
    val caretOffset = parameters.editor.caretModel.offset
    val command = document.getText(TextRange.create(promptModel.commandStartOffset, caretOffset))
    val tokens = shellSupport.getCommandTokens(parameters.editor.project!!, command) ?: return
    val allTokens = if (caretOffset != 0 && document.getText(TextRange.create(caretOffset - 1, caretOffset)) == " ") {
      tokens + ""  // user inserted space after the last token, so add empty incomplete token as last
    }
    else tokens
    val suggestions = runBlockingCancellable {
      computeSuggestions(allTokens, context)
    }

    val elements = suggestions.flatMap { it.toLookupElements() }
    resultSet.addAllElements(elements)

    if (elements.isNotEmpty()) {
      resultSet.stopHere()
    }
  }

  private suspend fun computeSuggestions(tokens: List<String>, context: TerminalCompletionContext): List<BaseSuggestion> {
    val aliases = context.runtimeDataProvider.getShellEnvironment()?.aliases ?: emptyMap()
    val expandedTokens = expandAliases(tokens, aliases, context)
    if (expandedTokens.isEmpty()) {
      return emptyList()
    }

    val completion = CommandSpecCompletion(IJCommandSpecManager.getInstance(), context.runtimeDataProvider)
    val command = expandedTokens.first()
    val arguments = expandedTokens.subList(1, expandedTokens.size)
    if (arguments.isEmpty()) {
      return completion.computeCommandsAndFiles(command)
    }
    else {
      val commandVariants = if (command.endsWith(".exe")) listOf(command.removeSuffix(".exe"), command) else listOf(command)
      val items = commandVariants.firstNotNullOfOrNull { completion.computeCompletionItems(it, arguments) } ?: emptyList()
      return when {
        items.isNotEmpty() -> items
        // Suggest file names if there is nothing to suggest, and completion is invoked manually.
        // But not for PowerShell, here it would be better to fall back to shell-based completion
        !context.parameters.isAutoPopup && context.session.shellIntegration.shellType != ShellType.POWERSHELL -> {
          completion.computeFileItems(expandedTokens.last())
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

  private fun BaseSuggestion.toLookupElements(): List<LookupElement> {
    val icon = findIconForSuggestion(this)
    return names.map { name ->
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
        .withIcon(icon)
        .withInsertHandler(MyInsertHandler(this, appendPathSeparator))
      PrioritizedLookupElement.withPriority(element, priority / 100.0)
    }
  }

  private class MyInsertHandler(private val suggestion: BaseSuggestion,
                                private val appendPathSeparator: Boolean) : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
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
    val runtimeDataProvider: ShellRuntimeDataProvider,
    val shellSupport: TerminalShellSupport,
    val parameters: CompletionParameters
  ) {
    val project: Project
      get() = parameters.editor.project!!
  }
}