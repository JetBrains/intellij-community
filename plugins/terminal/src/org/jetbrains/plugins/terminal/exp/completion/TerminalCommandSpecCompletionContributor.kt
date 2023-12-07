// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.text.StringUtil
import com.intellij.terminal.completion.CommandSpecCompletion
import org.jetbrains.plugins.terminal.exp.TerminalSession
import org.jetbrains.plugins.terminal.exp.completion.TerminalCompletionUtil.findIconForSuggestion
import org.jetbrains.plugins.terminal.exp.completion.TerminalCompletionUtil.getNextSuggestionsString
import org.jetbrains.terminal.completion.BaseSuggestion

internal class TerminalCommandSpecCompletionContributor : CompletionContributor(), DumbAware {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val session = parameters.editor.getUserData(TerminalSession.KEY)
    if (session == null || parameters.completionType != CompletionType.BASIC) {
      return
    }
    val shellSupport = TerminalShellSupport.findByShellType(session.shellIntegration.shellType) ?: return

    val prefix = result.prefixMatcher.prefix.substringAfterLast('/') // take last part if it is a file path
    val resultSet = result.withPrefixMatcher(PlainPrefixMatcher(prefix, true))

    val tokens = shellSupport.getCommandTokens(parameters.position) ?: return
    val suggestions = runBlockingCancellable {
      computeSuggestions(tokens, session, parameters)
    }

    val elements = suggestions.flatMap { it.toLookupElements() }
    resultSet.addAllElements(elements)
    resultSet.stopHere()
  }

  private suspend fun computeSuggestions(tokens: List<String>,
                                         session: TerminalSession,
                                         parameters: CompletionParameters): List<BaseSuggestion> {
    val runtimeDataProvider = IJShellRuntimeDataProvider(session)
    val completion = CommandSpecCompletion(IJCommandSpecManager.getInstance(), runtimeDataProvider)
    val items = completion.computeCompletionItems(tokens)?.takeIf { it.isNotEmpty() }
    return when {
      items != null -> items
      // suggest file names if there is nothing to suggest and completion is invoked manually
      !parameters.isAutoPopup -> completion.computeFileItems(tokens) ?: emptyList()
      else -> emptyList()
    }
  }

  private fun BaseSuggestion.toLookupElements(): List<LookupElement> {
    val icon = findIconForSuggestion(this)
    return names.map { name ->
      val cursorOffset = insertValue?.indexOf("{cursor}")
      val realInsertValue = insertValue?.replace("{cursor}", "")
      val nextSuggestions = getNextSuggestionsString(this).takeIf { it.isNotEmpty() }
      val escapedInsertValue = StringUtil.escapeChar(realInsertValue ?: name, ' ')
      val element = LookupElementBuilder.create(this, escapedInsertValue)
        .withPresentableText(displayName ?: name)
        .withTailText(nextSuggestions, true)
        .withIcon(icon)
        .withInsertHandler { context, _ ->
          if (cursorOffset != null && cursorOffset != -1) {
            context.editor.caretModel.moveToOffset(context.startOffset + cursorOffset)
          }
        }
      PrioritizedLookupElement.withPriority(element, priority / 100.0)
    }
  }
}