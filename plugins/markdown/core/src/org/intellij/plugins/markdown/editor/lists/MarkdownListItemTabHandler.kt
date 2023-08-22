// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.lists

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.siblings
import org.intellij.plugins.markdown.editor.lists.ListRenumberUtils.renumberInBulk
import org.intellij.plugins.markdown.editor.lists.ListUtils.items
import org.intellij.plugins.markdown.editor.lists.Replacement.Companion.replaceSafelyIn
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownList
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownListItem

/**
 * This handler increases nesting of the current/selected list item(s), if possible.
 * It is considered impossible if there is no previous item of the same nesting level (but maybe of a different type),
 * or if such item exists, but is too far (i.e. the distance is more, than two lines).
 * The children (paragraphs and lists) are indented as well.
 */
internal class MarkdownListItemTabHandler(baseHandler: EditorActionHandler?) : ListItemIndentUnindentHandlerBase(baseHandler) {

  override fun doIndentUnindent(item: MarkdownListItem, file: MarkdownFile, document: Document): Boolean {
    val itemInfo = ListItemInfo(item, document)

    val list = item.parent as MarkdownList
    val prevList = list.siblings(forward = false, withSelf = false).filterIsInstance<MarkdownList>().firstOrNull()

    val siblings = (prevList?.items ?: emptyList()) + list.items

    val prevItem = siblings.getOrNull(siblings.indexOf(item) - 1) ?: return false
    val prevInfo = ListItemInfo(prevItem, document)

    // this means the item will not become a subitem
    if (itemInfo.lines.first - prevInfo.lines.last > 2) return false

    itemInfo.changeIndent(prevInfo.indentInfo.subItemIndent()).replaceSafelyIn(document)
    return true
  }

  override fun updateNumbering(item: MarkdownListItem, file: MarkdownFile, document: Document) {
    val list = item.parent as MarkdownList
    list.renumberInBulk(document, recursive = false, restart = list.items.first() == item)

    PsiDocumentManager.getInstance(file.project).commitDocument(document)
    list.parentOfType<MarkdownList>()?.renumberInBulk(document, recursive = false, restart = false)
  }
}
