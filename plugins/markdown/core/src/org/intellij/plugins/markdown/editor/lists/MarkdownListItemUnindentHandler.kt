// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.lists

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.parentOfType
import org.intellij.plugins.markdown.editor.lists.ListRenumberUtils.renumberInBulk
import org.intellij.plugins.markdown.editor.lists.ListUtils.getLineIndentRange
import org.intellij.plugins.markdown.editor.lists.ListUtils.list
import org.intellij.plugins.markdown.editor.lists.ListUtils.sublists
import org.intellij.plugins.markdown.editor.lists.Replacement.Companion.replaceSafelyIn
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownListItem

/**
 * This handler decreases nesting of the current/selected list item(s), if it is nested at all.
 * The children (paragraphs and lists) are unindented as well.
 */
internal class MarkdownListItemUnindentHandler(baseHandler: EditorActionHandler?) : ListItemIndentUnindentHandlerBase(baseHandler) {

  override fun doIndentUnindent(item: MarkdownListItem, file: MarkdownFile, document: Document): Boolean {
    val outerItem = item.parentOfType<MarkdownListItem>()

    if (outerItem == null) {
      return removeLeadingSpaces(item, document)
    }

    decreaseNestingLevel(item, outerItem, file, document)
    return true
  }

  private fun removeLeadingSpaces(item: MarkdownListItem, document: Document): Boolean {
    val itemInfo = ListItemInfo(item, document)
    val indentRange = document.getLineIndentRange(itemInfo.lines.first)

    if (!indentRange.isEmpty) {
      itemInfo.changeIndent(0).replaceSafelyIn(document)
    }

    return !indentRange.isEmpty
  }

  private fun decreaseNestingLevel(item: MarkdownListItem, outerItem: MarkdownListItem, file: MarkdownFile, document: Document) {
    val itemInfo = ListItemInfo(item, document)
    val outerInfo = ListItemInfo(outerItem, document)

    val newSiblingsIndent = itemInfo.indentInfo.with(indent = outerInfo.indentInfo.indent).subItemIndent()

    for (siblingLine in outerInfo.lines.last downTo (itemInfo.lines.last + 1)) {
      itemInfo.indentInfo.changeLineIndent(siblingLine, newSiblingsIndent, document, file)?.apply(document)
    }
    itemInfo.changeIndent(outerInfo.indentInfo.indent).replaceSafelyIn(document)
  }

  override fun updateNumbering(item: MarkdownListItem, file: MarkdownFile, document: Document) {
    item.list.renumberInBulk(document, recursive = false, restart = false)

    PsiDocumentManager.getInstance(file.project).commitDocument(document)
    item.sublists.firstOrNull()?.renumberInBulk(document, recursive = false, restart = true)
  }
}
