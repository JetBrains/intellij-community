// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.handlers

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.siblings
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.intellij.plugins.markdown.editor.tables.TableUtils
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableCellImpl
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
    val cell = TableUtils.findCell(file, caretOffset)
    val nextCell = cell?.siblings(forward = forward, withSelf = false)?.filterIsInstance<MarkdownTableCellImpl>()?.firstOrNull()
    if (nextCell != null) {
      val offset = when {
        forward -> nextCell.startOffset
        else -> nextCell.endOffset
      }
      caret.moveToOffset(offset)
      return true
    } else if (forward) {
      cell?.parentRow?.endOffset?.let { caret.moveToOffset(it) }
      return true
    }
    return false
  }

  class Tab(baseHandler: EditorActionHandler?): MarkdownTableTabHandler(baseHandler, forward = true)

  class ShiftTab(baseHandler: EditorActionHandler?): MarkdownTableTabHandler(baseHandler, forward = false)
}
