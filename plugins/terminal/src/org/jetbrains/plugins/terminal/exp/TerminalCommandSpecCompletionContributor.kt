// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.terminal.completion.CommandSpecCompletion
import org.jetbrains.plugins.terminal.exp.completion.IJCommandSpecManager
import org.jetbrains.plugins.terminal.exp.completion.IJShellRuntimeDataProvider
import org.jetbrains.plugins.terminal.exp.completion.TerminalShellSupport
import org.jetbrains.terminal.completion.BaseSuggestion

class TerminalCommandSpecCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val session = parameters.editor.getUserData(TerminalSession.KEY)
    if (session == null || parameters.completionType != CompletionType.BASIC) {
      return
    }
    val shellType = session.shellIntegration?.shellType ?: return
    val shellSupport = TerminalShellSupport.findByShellType(shellType) ?: return

    val prefix = result.prefixMatcher.prefix.substringAfterLast('/') // take last part if it is a file path
    val resultSet = result.withPrefixMatcher(PlainPrefixMatcher(prefix, true))

    val tokens = shellSupport.getCommandTokens(parameters.position) ?: return
    val suggestions = runBlockingCancellable {
      val runtimeDataProvider = IJShellRuntimeDataProvider(session)
      val completion = CommandSpecCompletion(IJCommandSpecManager.getInstance(), runtimeDataProvider)
      completion.computeCompletionItems(tokens) ?: emptyList()
    }

    val elements = suggestions.flatMap { it.toLookupElements() }
    resultSet.addAllElements(elements)
    resultSet.stopHere()
  }

  private fun BaseSuggestion.toLookupElements(): List<LookupElement> {
    return names.map { name ->
      val cursorOffset = insertValue?.indexOf("{cursor}")
      val realInsertValue = insertValue?.replace("{cursor}", "")
      val element = LookupElementBuilder.create(this, realInsertValue ?: name)
        .withPresentableText(displayName ?: name)
        .withTypeText(description)
        .withInsertHandler { context, item ->
          val editor = context.editor
          if (cursorOffset != null && cursorOffset != -1) {
            editor.caretModel.moveToOffset(context.startOffset + cursorOffset)
          }
          else {
            if (!item.lookupString.endsWith('/')) {
              // insert space only if not a filepath was completed
              editor.document.insertString(context.tailOffset, " ")
              editor.caretModel.moveToOffset(context.tailOffset + 1)
            }
            AutoPopupController.getInstance(context.project).scheduleAutoPopup(editor)
          }
        }
      PrioritizedLookupElement.withPriority(element, priority / 100.0)
    }
  }
}