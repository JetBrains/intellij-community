// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.handlers

import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.editor.tables.TableFormattingUtils.reformatColumnOnChange
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.modifyColumn
import org.intellij.plugins.markdown.editor.tables.TableUtils
import org.intellij.plugins.markdown.editor.tables.TableUtils.separatorRow
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableSeparatorRow
import org.intellij.plugins.markdown.settings.MarkdownSettings

internal class MarkdownTableBackspaceHandler: BackspaceHandlerDelegate() {
  override fun beforeCharDeleted(c: Char, file: PsiFile, editor: Editor) = Unit

  override fun charDeleted(char: Char, file: PsiFile, editor: Editor): Boolean {
    if (!Registry.`is`("markdown.tables.editing.support.enable") || !MarkdownSettings.getInstance(file.project).isEnhancedEditingEnabled) {
      return false
    }
    if (file.fileType != MarkdownFileType.INSTANCE) {
      return false
    }
    val caretOffset = editor.caretModel.currentCaret.offset
    val document = editor.document
    if (!TableUtils.isProbablyInsideTableCell(document, caretOffset)) {
      return false
    }
    PsiDocumentManager.getInstance(file.project).commitDocument(document)
    val table = TableUtils.findTable(file, caretOffset) ?: return false
    val cellIndex = TableUtils.findCellIndex(file, caretOffset) ?: return false
    val alignment = table.separatorRow?.getCellAlignment(cellIndex) ?: return false
    val width = TableUtils.findCell(file, caretOffset)?.textRange?.length ?: table.separatorRow?.getCellRange(cellIndex)?.length ?: 0
    val text = document.charsSequence
    executeCommand(table.project) {
      table.modifyColumn(
        cellIndex,
        transformSeparator = { updateSeparator(document, it, width) },
        transformCell = { cell ->
          val range = cell.textRange
          if (range.length > width && text[range.endOffset - 1] == ' ' && text[range.endOffset - 2] == ' ') {
            document.deleteString(range.endOffset - 1, range.endOffset)
          }
        }
      )
      if (alignment != MarkdownTableSeparatorRow.CellAlignment.NONE) {
        PsiDocumentManager.getInstance(file.project).commitDocument(document)
        val reparsedTable = TableUtils.findTable(file, caretOffset)
        val isBlank = TableUtils.findCell(file, caretOffset)?.textRange?.let { text.substring(it.startOffset, it.endOffset) }?.isBlank() ?: true
        val shouldPreventExpand = isBlank && char == ' '
        reparsedTable?.reformatColumnOnChange(
          document,
          editor.caretModel.allCarets,
          cellIndex,
          trimToMaxContent = false,
          preventExpand = shouldPreventExpand
        )
      }
    }
    return true
  }

  private fun updateSeparator(document: Document, separatorRange: TextRange, width: Int) {
    val text = document.charsSequence
    if (separatorRange.length > width) {
      val endOffset = separatorRange.endOffset
      val offset = when {
        text[endOffset - 1] == '-' -> separatorRange.endOffset - 1
        text[endOffset - 2] == '-' -> separatorRange.endOffset - 2
        else -> return
      }
      document.deleteString(offset, offset + 1)
    }
  }
}
