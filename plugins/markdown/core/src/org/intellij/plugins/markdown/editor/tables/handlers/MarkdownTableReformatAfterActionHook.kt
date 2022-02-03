// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.handlers

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import org.intellij.plugins.markdown.editor.tables.TableFormattingUtils.reformatColumnOnChange
import org.intellij.plugins.markdown.editor.tables.TableUtils
import org.intellij.plugins.markdown.settings.MarkdownSettings

internal class MarkdownTableReformatAfterActionHook(private val baseHandler: EditorActionHandler?): EditorWriteActionHandler() {
  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
    return baseHandler?.isEnabled(editor, caret, dataContext) == true
  }

  override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext?) {
    baseHandler?.execute(editor, caret, dataContext)
    actuallyExecute(editor, caret, dataContext)
  }

  private fun actuallyExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
    val project = editor.project ?: return
    if (!Registry.`is`("markdown.tables.editing.support.enable") || !MarkdownSettings.getInstance(project).isEnhancedEditingEnabled) {
      return
    }
    val document = editor.document
    val caretOffset = caret?.offset ?: return
    if (!TableUtils.isProbablyInsideTableCell(document, caretOffset) || editor.caretModel.caretCount != 1) {
      return
    }
    val documentManager = PsiDocumentManager.getInstance(project)
    val file = documentManager.getPsiFile(document) ?: return
    PsiDocumentManager.getInstance(project).commitDocument(document)
    val cell = TableUtils.findCell(file, caretOffset)
    val table = cell?.parentTable
    val columnIndex = cell?.columnIndex
    if (cell == null || table == null || columnIndex == null) {
      return
    }
    val text = document.charsSequence
    if (cell.textRange.let { text.substring(it.startOffset, it.endOffset) }.isBlank()) {
      return
    }
    executeCommand(table.project) {
      table.reformatColumnOnChange(
        document,
        editor.caretModel.allCarets,
        columnIndex,
        trimToMaxContent = false,
        preventExpand = false
      )
    }
  }
}
