// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.handlers

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.siblings
import com.intellij.refactoring.suggested.endOffset
import org.intellij.plugins.markdown.editor.tables.TableUtils
import org.intellij.plugins.markdown.editor.tables.TableUtils.firstNonWhitespaceOffset
import org.intellij.plugins.markdown.editor.tables.TableUtils.lastNonWhitespaceOffset
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableCell
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableRow
import org.intellij.plugins.markdown.settings.MarkdownSettings

internal abstract class MarkdownTableTabHandler(private val baseHandler: EditorActionHandler?, private val forward: Boolean = true): EditorWriteActionHandler() {
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
    if (!Registry.`is`("markdown.tables.editing.support.enable") || !MarkdownSettings.getInstance(project).isEnhancedEditingEnabled) {
      return false
    }
    val document = editor.document
    val caretOffset = caret?.offset ?: return false
    if (!TableUtils.isProbablyInsideTableCell(document, caretOffset) || editor.caretModel.caretCount != 1) {
      return false
    }
    val documentManager = PsiDocumentManager.getInstance(project)
    val file = documentManager.getPsiFile(document) ?: return false
    PsiDocumentManager.getInstance(project).commitDocument(document)
    val cell = TableUtils.findCell(file, caretOffset) ?: return false
    val nextCell = findNextCell(cell, forward)
    if (nextCell != null) {
      val offset = when {
        forward -> nextCell.firstNonWhitespaceOffset
        else -> nextCell.lastNonWhitespaceOffset + 1
      }
      caret.moveToOffset(offset)
      return true
    } else if (forward) {
      cell.parentRow?.endOffset?.let { caret.moveToOffset(it) }
      return true
    }
    return false
  }

  private fun findNextCell(currentCell: MarkdownTableCell, forward: Boolean): MarkdownTableCell? {
    val nextCellInCurrentRow = findSiblingCell(currentCell, forward, withSelf = false)
    if (nextCellInCurrentRow != null) {
      return nextCellInCurrentRow
    }
    val nextRow = currentCell.parentRow?.siblings(forward, withSelf = false)?.filterIsInstance<MarkdownTableRow>()?.firstOrNull()
    val startElement = when {
      forward -> nextRow?.firstChild
      else -> nextRow?.lastChild
    }
    return startElement?.let { findSiblingCell(it, forward, withSelf = true) }
  }

  private fun findSiblingCell(element: PsiElement, forward: Boolean, withSelf: Boolean): MarkdownTableCell? {
    return element.siblings(forward, withSelf).filterIsInstance<MarkdownTableCell>().firstOrNull()
  }

  class Tab(baseHandler: EditorActionHandler?): MarkdownTableTabHandler(baseHandler, forward = true)

  class ShiftTab(baseHandler: EditorActionHandler?): MarkdownTableTabHandler(baseHandler, forward = false)
}
