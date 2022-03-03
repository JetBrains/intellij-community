// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.lists

import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.descendantsOfType
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentsOfType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.DocumentUtil
import com.intellij.util.text.CharArrayUtil
import org.intellij.plugins.markdown.editor.lists.ListRenumberUtils.renumberInBulk
import org.intellij.plugins.markdown.editor.lists.ListUtils.getListItemAtLine
import org.intellij.plugins.markdown.editor.lists.ListUtils.items
import org.intellij.plugins.markdown.editor.lists.ListUtils.list
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownList
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownListItem
import org.intellij.plugins.markdown.settings.MarkdownSettings

/**
 * This handler removes the whole marker of the current list item.
 */
internal class MarkdownListMarkerBackspaceHandlerDelegate : BackspaceHandlerDelegate() {

  private var item: MarkdownListItem? = null

  override fun beforeCharDeleted(c: Char, file: PsiFile, editor: Editor) {
    item = null

    val deletedOffset = editor.caretModel.offset - 1
    if (file !is MarkdownFile || deletedOffset < 0
        || !MarkdownSettings.getInstance(file.project).isEnhancedEditingEnabled) {
      return
    }

    PsiDocumentManager.getInstance(file.project).commitDocument(editor.document)
    val marker = file.findElementAt(deletedOffset) ?: return

    if (marker.elementType in MarkdownTokenTypeSets.LIST_MARKERS) {
      val nonWsStartOffset = CharArrayUtil.shiftForward(editor.document.charsSequence, marker.startOffset, " \t")
      if (nonWsStartOffset <= deletedOffset) {
        item = marker.parentOfType()
      }
    }
  }

  override fun charDeleted(c: Char, file: PsiFile, editor: Editor): Boolean {
    val item = item ?: return false // for smart cast
    val document = editor.document

    val createsNewList = listWillBeSplitToTwoLists(item, document)

    val nextItemFirstLine = item.list.descendantsOfType<MarkdownListItem>()
      .dropWhile { it != item }
      .drop(1) // always contains item itself
      .firstOrNull()
      ?.let { document.getLineNumber(it.startOffset) }

    val range = item.markerElement!!.textRange
    runWriteAction {
      document.deleteString(range.startOffset, range.endOffset - 1)
    }

    if (nextItemFirstLine != null && !createsNewList) {
      PsiDocumentManager.getInstance(file.project).commitDocument(document)

      (file as MarkdownFile).getListItemAtLine(nextItemFirstLine, document)
        ?.list?.renumberInBulk(document, recursive = false, restart = false)
    }

    return true
  }

  /*
  Consider an example:
  ```
  1. first
     1.

     2. second sub-item
  2. second
  ```

  If the first sub-item is deleted, then the second part (items, starting with "2.")
  becomes a separate list, recognized as a flat list with no nesting. Applying a renumbering
  to it will increase the indent of the "second" item. Such behaviour is unwanted.
  */
  private fun listWillBeSplitToTwoLists(item: MarkdownListItem, document: Document): Boolean {
    val itemFirstLine = document.getLineNumber(item.startOffset)
    val itemLastLine = document.getLineNumber(minOf(item.endOffset - 1, document.textLength))

    val emptyLineBefore = itemFirstLine > 0 && DocumentUtil.isLineEmpty(document, itemFirstLine - 1)
    val emptyLineAfter = itemLastLine + 1 < document.lineCount && DocumentUtil.isLineEmpty(document, itemFirstLine + 1)
    val nearEmptyLine = emptyLineBefore || emptyLineAfter

    val onlyMarkerLine =
      CharArrayUtil.shiftForward(document.text, item.markerElement!!.endOffset - 1, " \t") == document.getLineEndOffset(itemFirstLine)
    val hasPreviousItem = item.parentsOfType<MarkdownList>().last().items.first() != item
    return hasPreviousItem && nearEmptyLine && onlyMarkerLine
  }
}
