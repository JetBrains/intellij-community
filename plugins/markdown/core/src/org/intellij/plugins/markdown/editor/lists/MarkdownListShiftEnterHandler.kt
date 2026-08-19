// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.lists

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiUtilCore
import org.intellij.plugins.markdown.editor.lists.ListUtils.getLineIndentSpaces
import org.intellij.plugins.markdown.editor.lists.ListUtils.getListItemAt
import org.intellij.plugins.markdown.editor.lists.ListUtils.normalizedMarker
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils
import org.intellij.plugins.markdown.lang.supportsMarkdown
import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings

internal class MarkdownListShiftEnterHandler(private val baseHandler: EditorActionHandler?): EditorWriteActionHandler() {
  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
    return baseHandler?.isEnabled(editor, caret, dataContext) == true
  }

  override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext?) {
    if (!actuallyExecute(editor, caret, dataContext)) {
      baseHandler?.execute(editor, caret, dataContext)
    }
  }

  private fun actuallyExecute(editor: Editor, caret: Caret?, dataContext: DataContext?): Boolean {
    val project = editor.project ?: return false
    if (!MarkdownCodeInsightSettings.getInstance().state.smartEnterAndBackspace) {
      return false
    }
    if (editor.caretModel.caretCount != 1) {
      return false
    }

    val document = editor.document
    val documentManager = PsiDocumentManager.getInstance(project)
    val file = documentManager.getPsiFile(document) ?: return false
    if (!file.supportsMarkdown(dataContext)) {
      return false
    }

    documentManager.commitDocument(document)
    val currentCaret = caret ?: editor.caretModel.currentCaret
    val element = PsiUtilCore.getElementAtOffset(file, currentCaret.offset)
    if (MarkdownCodeFenceUtils.getCodeFence(element) != null) {
      return false
    }
    val line = document.getLineNumber(currentCaret.offset)
    val item = file.getListItemAt(currentCaret.offset, document) ?: return false
    if (item.children.isEmpty()) {
      return false
    }
    val itemLine = document.getLineNumber(item.textRange.startOffset)
    val lineIndent = document.getLineIndentSpaces(line, file).orEmpty()
    val contentIndent = when (line) {
      itemLine -> lineIndent + " ".repeat(item.normalizedMarker.length)
      else -> lineIndent
    }

    currentCaret.removeSelection()
    val lineEndOffset = document.getLineEndOffset(line)
    document.insertString(lineEndOffset, "\n$contentIndent")
    currentCaret.moveToOffset(lineEndOffset + 1 + contentIndent.length)
    return true
  }
}
