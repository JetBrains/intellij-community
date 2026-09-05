// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.tables.handlers

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorTextInsertHandler
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.Producer
import org.intellij.plugins.markdown.editor.tables.TableFormattingUtils.reformatColumnOnChange
import org.intellij.plugins.markdown.editor.tables.TableUtils
import org.intellij.plugins.markdown.editor.tables.TableUtils.getTableStyle
import java.awt.datatransfer.Transferable

internal class MarkdownTableReformatAfterActionHook(
  private val baseHandler: EditorActionHandler?,
): EditorActionHandler(), EditorTextInsertHandler {
  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
    return baseHandler?.isEnabled(editor, caret, dataContext) == true
  }

  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
    baseHandler?.execute(editor, caret, dataContext)
    actuallyExecute(editor, caret ?: editor.caretModel.currentCaret)
  }

  override fun execute(editor: Editor, dataContext: DataContext?, producer: Producer<out Transferable>?) {
    when (baseHandler) {
      is EditorTextInsertHandler -> baseHandler.execute(editor, dataContext, producer)
      else -> baseHandler?.execute(editor, null, dataContext)
    }
    actuallyExecute(editor, editor.caretModel.currentCaret)
  }

  private fun actuallyExecute(editor: Editor, caret: Caret) {
    val project = editor.project ?: return
    val document = editor.document
    val caretOffset = caret.offset
    if (!TableUtils.isProbablyInsideTableCell(document, caretOffset) || editor.caretModel.caretCount != 1) {
      return
    }
    val documentManager = PsiDocumentManager.getInstance(project)
    val file = documentManager.getPsiFile(document) ?: return
    if (!TableUtils.isFormattingOnTypeEnabledForTables(file)) {
      return
    }
    documentManager.commitDocument(document)
    val cell = TableUtils.findCell(file, caretOffset)
    val table = cell?.parentTable
    val columnIndex = cell?.columnIndex
    if (cell == null || table == null || columnIndex == null) {
      return
    }
    val tableStyle = getTableStyle(file)
    val text = document.charsSequence
    if (cell.textRange.let { text.substring(it.startOffset, it.endOffset) }.isBlank()) {
      return
    }
    runWriteAction {
      executeCommand(table.project) {
        table.reformatColumnOnChange(
          document,
          editor.caretModel.allCarets,
          columnIndex,
          trimToMaxContent = false,
          tableStyle = tableStyle,
          preventExpand = false,
        )
      }
    }
  }
}
