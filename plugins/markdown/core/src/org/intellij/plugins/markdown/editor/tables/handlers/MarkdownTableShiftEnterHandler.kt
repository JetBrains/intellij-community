// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.handlers

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.parents
import com.intellij.psi.util.siblings
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.buildEmptyRow
import org.intellij.plugins.markdown.editor.tables.TableUtils
import org.intellij.plugins.markdown.editor.tables.TableUtils.isHeaderRow
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableRow
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableSeparatorRow
import org.intellij.plugins.markdown.settings.MarkdownSettings

internal class MarkdownTableShiftEnterHandler(private val baseHandler: EditorActionHandler?): EditorWriteActionHandler() {
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
    val currentCaret = caret ?: editor.caretModel.currentCaret
    val caretOffset = currentCaret.offset
    if ((!TableUtils.isProbablyInsideTableCell(document, caretOffset) &&
         !isProbablyAtTheEndOfTableRow(document, caretOffset)) ||
        editor.caretModel.caretCount != 1
    ) {
      return false
    }
    val documentManager = PsiDocumentManager.getInstance(project)
    val file = documentManager.getPsiFile(document) ?: return false
    PsiDocumentManager.getInstance(project).commitDocument(document)
    val row = findRow(file, document, caretOffset) ?: return false
    val table = TableUtils.findTable(row) ?: return false
    val emptyRow = table.buildEmptyRow().toString()
    executeCommand(table.project) {
      val content = "\n$emptyRow"
      val line = document.getLineNumber(caretOffset)
      val lineEndOffset = document.getLineEndOffset(line)
      document.insertString(lineEndOffset, content)
      currentCaret.moveToOffset(document.getLineEndOffset(line + 1))
    }
    return true
  }

  private fun findRow(file: PsiFile, document: Document, caretOffset: Int): PsiElement? {
    val actual = actuallyFindRow(file, caretOffset)
    if (actual != null) {
      return actual
    }
    if (caretOffset == 0 || document.getLineNumber(caretOffset) != document.getLineNumber(caretOffset - 1)) {
      return null
    }
    return actuallyFindRow(file, caretOffset - 1)
  }

  private inline fun <reified T> findElement(element: PsiElement): T? {
    val sibling = element.siblings(forward = false, withSelf = true).filterIsInstance<T>().firstOrNull()
    return sibling ?: element.parents(withSelf = true).filterIsInstance<T>().firstOrNull()
  }

  private fun actuallyFindRow(file: PsiFile, caretOffset: Int): PsiElement? {
    val element = PsiUtilCore.getElementAtOffset(file, caretOffset)
    val primary = findElement<MarkdownTableRow>(element)?.takeUnless { it.isHeaderRow }
    return primary ?: findElement<MarkdownTableSeparatorRow>(element)
  }

  private fun isProbablyAtTheEndOfTableRow(document: Document, caretOffset: Int): Boolean {
    val text = document.charsSequence
    val line = document.getLineNumber(caretOffset)
    val lineEndOffset = document.getLineEndOffset(line)
    for (offset in caretOffset until lineEndOffset) {
      when (text[offset]) {
        ' ', '\t' -> continue
        else -> return false
      }
    }
    return true
  }
}
