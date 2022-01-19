// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.lists

import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentsOfType
import com.intellij.refactoring.suggested.startOffset
import org.intellij.plugins.markdown.editor.lists.ListUtils.getLineIndentRange
import org.intellij.plugins.markdown.editor.lists.ListUtils.getLineIndentSpaces
import org.intellij.plugins.markdown.editor.lists.ListUtils.getListItemAtLine
import org.intellij.plugins.markdown.editor.lists.ListUtils.getListItemAtLineSafely
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownListItem
import org.intellij.plugins.markdown.settings.MarkdownSettings

/**
 * This handler does two things for a caret inside the indent of some line in a list:
 *   - if the line is the first line of some list item, the whole item is unindented
 *   - otherwise, the line is unindented
 */
internal class MarkdownListIndentBackspaceHandlerDelegate : BackspaceHandlerDelegate() {
  private var deletedRange: TextRange? = null
  private var listItem: MarkdownListItem? = null
  private var moveCaret = false

  override fun beforeCharDeleted(c: Char, file: PsiFile, editor: Editor) {
    deletedRange = null
    listItem = null
    moveCaret = false

    if (file !is MarkdownFile || !c.isWhitespace()
        || !MarkdownSettings.getInstance(file.project).isEnhancedEditingEnabled) {
      return
    }

    val document = editor.document
    PsiDocumentManager.getInstance(file.project).commitDocument(document)

    val caretOffset = editor.caretModel.offset
    val line = document.getLineNumber(caretOffset)
    if (line == 0) return

    val lineStart = document.getLineStartOffset(line)
    if (document.getText(TextRange.create(lineStart, caretOffset)).isNotBlank()) return

    val indent = editor.caretModel.logicalPosition.column
    if (indent == 0) return

    val item = file.getListItemAtLine(line, document)
    if (item != null && document.getLineNumber(item.startOffset) == line) {
      listItem = item // it should just be unindented
      return
    }

    val aboveItem = file.getListItemAtLine(line - 1, document)
                    ?: file.getListItemAtLineSafely(line - 2, document)
                    ?: return

    if (indent + document.getLineStartOffset(line) > document.getLineEndOffset(line)) {
      moveCaret = true
      return
    }

    val wantedIndent = indentLevels(aboveItem, document).find { it < indent } ?: return
    deletedRange = TextRange.from(lineStart + wantedIndent, indent - wantedIndent)
  }

  // a sequence of indent sizes for the caret to iterate over, from greatest to smallest
  private fun indentLevels(aboveItem: MarkdownListItem, document: Document) =
    aboveItem.parentsOfType<MarkdownListItem>(withSelf = true)
      .map { ListItemInfo(it, document).indentInfo.indent }


  override fun charDeleted(c: Char, file: PsiFile, editor: Editor): Boolean {
    if (listItem != null) {
      runWriteAction {
        EditorModificationUtil.insertStringAtCaret(editor, c.toString())
      }
      PsiDocumentManager.getInstance(file.project).commitDocument(editor.document)
      val unindentHandler = MarkdownListItemUnindentHandler(null)
      unindentHandler.execute(editor, editor.caretModel.currentCaret, null)
      return true
    }

    val document = editor.document
    if (moveCaret) {
      // Editor | General | Allow caret placement: after the end of line = true
      val line = document.getLineNumber(editor.caretModel.offset)
      editor.caretModel.moveToOffset(document.getLineEndOffset(line))
      return true
    }

    val deletedRange = deletedRange ?: return false

    val line = document.getLineNumber(editor.caretModel.offset)
    val indentRange = document.getLineIndentRange(line)
    val realIndent = document.getLineIndentSpaces(line, file)!!

    runWriteAction {
      if (indentRange.length < realIndent.length) {
        editor.document.replaceString(indentRange.startOffset, indentRange.endOffset, realIndent)
      }

      editor.document.deleteString(deletedRange.startOffset, deletedRange.endOffset - 1)
    }
    return true
  }
}
