// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion

import com.intellij.codeInsight.inline.completion.*
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSingleSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.TextRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.block.history.CommandHistoryPresenter.Companion.isTerminalCommandHistory
import org.jetbrains.plugins.terminal.block.history.CommandSearchPresenter.Companion.isTerminalCommandSearch
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.editor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isPromptEditor

@Service(Service.Level.PROJECT)
internal class TerminalInlineCompletion(private val scope: CoroutineScope) {
  fun install(editor: EditorEx) {
    InlineCompletion.install(editor, scope)
  }

  companion object {
    fun getInstance(project: Project): TerminalInlineCompletion = project.service()
  }
}

internal class TerminalInlineCompletionProvider : InlineCompletionProvider {
  override val id: InlineCompletionProviderID = InlineCompletionProviderID("TerminalInlineCompletionProvider")

  override suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion {
    return InlineCompletionSingleSuggestion.build {
      withContext(Dispatchers.EDT) {
        getCompletionElement(request.editor)
      }?.let {
        emit(it)
      }
    }
  }

  private suspend fun getCompletionElement(editor: Editor): InlineCompletionElement? {
    val isAtTheEnd = readAction {
      val caretOffset = editor.caretModel.offset
      val document = editor.document
      caretOffset >= document.textLength || document.getText(TextRange(caretOffset, caretOffset + 1)) == " "
    }
    if (!isAtTheEnd) return null

    val lookup = LookupManager.getActiveLookup(editor) ?: return null
    if (lookup.isTerminalCommandHistory || lookup.isTerminalCommandSearch) {
      return null
    }

    val item = lookup.currentItem ?: return null
    val itemPrefix = lookup.itemPattern(item)
    if (SystemInfo.isFileSystemCaseSensitive && !item.lookupString.startsWith(itemPrefix)) {
      // do not show inline completion if a prefix is written in a different case in the case-sensitive file system
      return null
    }
    if (!item.lookupString.startsWith(itemPrefix, ignoreCase = !SystemInfo.isFileSystemCaseSensitive)) {
      // do not show inline completion if insert string is not match the typed prefix
      return null
    }
    val itemSuffix = item.lookupString.removeRange(0, itemPrefix.length)
    return InlineCompletionGrayTextElement(itemSuffix)
  }

  override fun isEnabled(event: InlineCompletionEvent): Boolean {
    return event.toRequest()?.editor?.isPromptEditor == true
  }

  override fun restartOn(event: InlineCompletionEvent): Boolean {
    return event is InlineCompletionEvent.InlineLookupEvent
  }
}

internal class TerminalInsertInlineCompletionAction : EditorAction(Handler()), ActionPromoter {
  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
    // promote only if it is a prompt editor, because otherwise it will break the "EditorRight" action invocation in the RemoteDev,
    // because it count this action as enabled by default and 'isEnabledForCaret' check is not happen.
    // todo: revise when new terminal will be enabled in the RemoteDev
    return if (context.editor?.isPromptEditor == true) {
      listOf(this)
    }
    else emptyList()
  }

  private class Handler : EditorWriteActionHandler() {
    override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext?) {
      InlineCompletion.getHandlerOrNull(editor)?.insert()
    }

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
      return editor.isPromptEditor && InlineCompletionContext.getOrNull(editor) != null
    }
  }
}
