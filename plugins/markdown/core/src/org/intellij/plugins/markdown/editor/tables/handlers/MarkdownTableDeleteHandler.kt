// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.handlers

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.psi.PsiDocumentManager

internal class MarkdownTableDeleteHandler(private val baseHandler: EditorActionHandler?) : EditorWriteActionHandler() {
  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
    return baseHandler?.isEnabled(editor, caret, dataContext) == true
  }

  override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext?) {
    val project = editor.project
    val document = editor.document
    val caretOffset = editor.caretModel.currentCaret.offset
    val hasSelection = editor.caretModel.allCarets.any { it.hasSelection() }
    val deletedChar = if (!hasSelection && caretOffset < document.textLength) {
      document.charsSequence[caretOffset]
    } else null
    baseHandler?.execute(editor, caret, dataContext)
    if (project == null || deletedChar == null || editor.caretModel.caretCount != 1) return
    val file = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return
    reformatTableColumnAfterCharDeletion(deletedChar, file, editor)
  }
}
