// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.handlers

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.editor.tables.TableUtils
import org.intellij.plugins.markdown.settings.MarkdownSettings

internal class MarkdownTableEnterHandler: EnterHandlerDelegateAdapter() {
  private var firstEnterPosition: Int? = null

  override fun preprocessEnter(
    file: PsiFile,
    editor: Editor,
    caretOffset: Ref<Int>,
    caretAdvance: Ref<Int>,
    dataContext: DataContext,
    originalHandler: EditorActionHandler?
  ): EnterHandlerDelegate.Result {
    if (!Registry.`is`("markdown.tables.editing.support.enable") || !MarkdownSettings.getInstance(file.project).isEnhancedEditingEnabled) {
      return super.preprocessEnter(file, editor, caretOffset, caretAdvance, dataContext, originalHandler)
    }
    val enterPosition = firstEnterPosition
    firstEnterPosition = null
    val document = editor.document
    val actualCaretOffset = editor.caretModel.currentCaret.offset
    if (!TableUtils.isProbablyInsideTableCell(document, actualCaretOffset)) {
      return super.preprocessEnter(file, editor, caretOffset, caretAdvance, dataContext, originalHandler)
    }
    PsiDocumentManager.getInstance(file.project).commitDocument(document)
    val cell = TableUtils.findCell(file, actualCaretOffset)
    val table = cell?.parentTable
    val cellIndex = cell?.columnIndex
    if (cell == null || table == null || cellIndex == null) {
      return super.preprocessEnter(file, editor, caretOffset, caretAdvance, dataContext, originalHandler)
    }
    if (actualCaretOffset == enterPosition) {
      val start = actualCaretOffset - insertTag.length
      val end = actualCaretOffset
      if (actualCaretOffset > insertTag.length && document.getText(TextRange(start, end)) == insertTag) {
        executeCommand {
          document.deleteString(start, end)
        }
        caretOffset.set(start)
      }
      return super.preprocessEnter(file, editor, caretOffset, caretAdvance, dataContext, originalHandler)
    }
    caretAdvance.set(insertTag.length)
    executeCommand {
      EditorModificationUtil.insertStringAtCaret(editor, insertTag)
    }
    firstEnterPosition = actualCaretOffset + insertTag.length
    return EnterHandlerDelegate.Result.Stop
  }

  companion object {
    private const val insertTag = "<br/>"
  }
}
