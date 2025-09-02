// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.prompt

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionPhase.EmptyAutoPopup
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isSuppressCompletion
import java.io.File

/**
 * Logic is mostly copied from [com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler].
 * Added additional characters to trigger auto popup ('-', '/').
 * Allowed to reopen the popup automatically in the [EmptyAutoPopup] phase.
 */
internal class TerminalCompletionAutoPopupHandler : TypedHandlerDelegate() {
  override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (!editor.isPromptEditor) {
      return Result.CONTINUE
    }

    val lookup = LookupManager.getActiveLookup(editor)
    if (lookup is LookupImpl) {
      if (editor.selectionModel.hasSelection()) {
        lookup.performGuardedChange { EditorModificationUtil.deleteSelectedText(editor) }
      }
      return Result.STOP
    }

    if (Character.isLetterOrDigit(charTyped) || charTyped == '-' || charTyped == File.separatorChar) {
      if (!editor.isSuppressCompletion) {
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
      }
      return Result.STOP
    }

    return Result.CONTINUE
  }

  override fun beforeClosingQuoteInserted(quote: CharSequence, project: Project, editor: Editor, file: PsiFile): Result {
    // do not insert backticks in pairs because it is a line continuation character in PowerShell
    return if (!editor.isPromptEditor || quote != "`") {
      Result.CONTINUE
    }
    else Result.STOP
  }
}
