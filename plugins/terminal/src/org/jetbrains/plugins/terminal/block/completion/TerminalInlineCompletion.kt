// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion

import com.intellij.codeInsight.inline.completion.*
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
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