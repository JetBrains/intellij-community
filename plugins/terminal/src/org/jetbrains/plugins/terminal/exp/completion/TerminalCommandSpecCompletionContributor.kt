// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.terminal.completion.CommandSpecCompletion
import com.intellij.terminal.completion.ShellRuntimeDataProvider
import org.jetbrains.plugins.terminal.exp.TerminalSession
import org.jetbrains.plugins.terminal.exp.completion.TerminalCompletionUtil.findIconForSuggestion
import org.jetbrains.plugins.terminal.exp.completion.TerminalCompletionUtil.getNextSuggestionsString
import org.jetbrains.terminal.completion.BaseSuggestion

internal class TerminalCommandSpecCompletionContributor : CompletionContributor(), DumbAware {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val session = parameters.editor.getUserData(TerminalSession.KEY)
    val runtimeDataProvider = parameters.editor.getUserData(IJShellRuntimeDataProvider.KEY)
    if (session == null || runtimeDataProvider == null || parameters.completionType != CompletionType.BASIC) {
      return
    }
    val shellSupport = TerminalShellSupport.findByShellType(session.shellIntegration.shellType) ?: return
    val context = TerminalCompletionContext(session, runtimeDataProvider, shellSupport, parameters)

    val prefix = result.prefixMatcher.prefix.substringAfterLast('/') // take last part if it is a file path
    val resultSet = result.withPrefixMatcher(PlainPrefixMatcher(prefix, true))

    val tokens = shellSupport.getCommandTokens(parameters.position) ?: return
    val suggestions = runBlockingCancellable {
      computeSuggestions(tokens, context)
    }

    val elements = suggestions.flatMap { it.toLookupElements() }
    resultSet.addAllElements(elements)
    resultSet.stopHere()
  }

  private suspend fun computeSuggestions(tokens: List<String>, context: TerminalCompletionContext): List<BaseSuggestion> {
    val aliases = context.runtimeDataProvider.getShellEnvironment()?.aliases ?: return emptyList()
    val expandedTokens = expandAliases(tokens, aliases, context)

    val completion = CommandSpecCompletion(IJCommandSpecManager.getInstance(), context.runtimeDataProvider)
    val items = completion.computeCompletionItems(expandedTokens)?.takeIf { it.isNotEmpty() }
    return when {
      items != null -> items
      // suggest file names if there is nothing to suggest and completion is invoked manually
      !context.parameters.isAutoPopup -> completion.computeFileItems(expandedTokens) ?: emptyList()
      else -> emptyList()
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
      val element = LookupElementBuilder.create(this, escapedInsertValue)
        .withPresentableText(displayName ?: name)
        .withTailText(nextSuggestions, true)
        .withIcon(icon)
        .withInsertHandler(MyInsertHandler(this))
      PrioritizedLookupElement.withPriority(element, priority / 100.0)
    }
  }

  private class MyInsertHandler(private val suggestion: BaseSuggestion) : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
      val cursorOffset = suggestion.insertValue?.indexOf("{cursor}")
      if (cursorOffset != null && cursorOffset != -1) {
        context.editor.caretModel.moveToOffset(context.startOffset + cursorOffset)
      }
    }
  }

  private class TerminalCompletionContext(
    val session: TerminalSession,
    val runtimeDataProvider: ShellRuntimeDataProvider,
    val shellSupport: TerminalShellSupport,
    val parameters: CompletionParameters
  ) {
    val project: Project
      get() = parameters.editor.project!!
  }
}