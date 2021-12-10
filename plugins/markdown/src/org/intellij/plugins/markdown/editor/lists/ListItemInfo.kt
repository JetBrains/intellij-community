// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.lists

import com.intellij.openapi.editor.Document
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.intellij.plugins.markdown.editor.lists.ListUtils.getLineIndentSpaces
import org.intellij.plugins.markdown.editor.lists.ListUtils.normalizedMarker
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownListItem

/**
 * Represents a list item. Allows to easily change whole item's indent (including sub-items).
 */
internal class ListItemInfo(item: MarkdownListItem, val document: Document) {
  private val file = item.containingFile

  val lines = document.getLineNumber(item.startOffset)..document.getLineNumber(item.endOffset)

  var indentInfo: ListItemIndentInfo = run {
    val indent = document.getLineIndentSpaces(lines.first, file)!!
    ListItemIndentInfo(indent.length, item.normalizedMarker.length)
  }

  fun changeIndent(newIndent: Int): List<Replacement> =
    lines.mapNotNull {
      indentInfo.changeLineIndent(it, newIndent, document, file)
    }.also {
      indentInfo = indentInfo.with(indent = newIndent)
    }
}
