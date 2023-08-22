// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionPhase.EmptyAutoPopup
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Logic is mostly copied from [com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler].
 * Added only additional characters to trigger auto popup (' ', '-', '/').
 */
class TerminalCompletionAutoPopupHandler : TypedHandlerDelegate() {
  override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (editor.getUserData(TerminalPromptController.KEY) == null) {
      return Result.CONTINUE
    }

    val phase = CompletionServiceImpl.getCompletionPhase()
    val lookup = LookupManager.getActiveLookup(editor)
    if (lookup is LookupImpl) {
      if (editor.selectionModel.hasSelection()) {
        lookup.performGuardedChange { EditorModificationUtil.deleteSelectedText(editor) }
      }
      return Result.STOP
    }

    if (Character.isLetterOrDigit(charTyped) || charTyped == ' ' || charTyped == '-' || charTyped == '/') {
      if (phase is EmptyAutoPopup && phase.allowsSkippingNewAutoPopup(editor, charTyped)) {
        return Result.CONTINUE
      }
      AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
      return Result.STOP
    }

    return Result.CONTINUE
  }
}