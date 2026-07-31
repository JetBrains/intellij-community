// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.lists

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.startOffset
import org.intellij.plugins.markdown.editor.lists.ListRenumberUtils.renumberInBulk
import org.intellij.plugins.markdown.editor.lists.ListUtils.items
import org.intellij.plugins.markdown.editor.lists.Replacement.Companion.replaceSafelyIn
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownList
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownListItem

/**
 * This handler increases nesting of the current/selected list item(s) by a single level.
 * When the caret is inside the indent before the item marker (and there is no selection),
 * only the item's own line is indented; otherwise the children (paragraphs and lists) are indented as well.
 */
internal class MarkdownListItemTabHandler(baseHandler: EditorActionHandler?) : ListItemIndentUnindentHandlerBase(baseHandler) {

  override fun doIndentUnindent(item: MarkdownListItem, file: PsiFile, document: Document, caret: Caret): Boolean {
    val itemInfo = ListItemInfo(item, document)
    val newIndent = itemInfo.indentInfo.subItemIndent()

    if (!caret.hasSelection() && caret.offset <= item.startOffset) {
      itemInfo.indentInfo.changeLineIndent(itemInfo.lines.first, newIndent, document, file)?.apply(document)
    }
    else {
      itemInfo.changeIndent(newIndent).replaceSafelyIn(document)
    }
    return true
  }

  override fun updateNumbering(item: MarkdownListItem, file: PsiFile, document: Document) {
    val list = item.parent as MarkdownList
    list.renumberInBulk(document, recursive = false, restart = list.items.first() == item)

    PsiDocumentManager.getInstance(file.project).commitDocument(document)
    list.parentOfType<MarkdownList>()?.renumberInBulk(document, recursive = false, restart = false)
  }
}
