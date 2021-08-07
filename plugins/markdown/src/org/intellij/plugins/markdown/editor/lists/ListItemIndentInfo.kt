// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.lists

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.editor.lists.ListUtils.getLineIndentRange
import org.intellij.plugins.markdown.editor.lists.ListUtils.getLineIndentSpaces

/**
 * An immutable class, that represents some abstract contents inside a list in Markdown,
 * that has an indent and may have children with a greater indent. If [markerLength] > 0,
 * then it represents a list item, otherwise it represents a paragraph.
 */
internal data class ListItemIndentInfo(val indent: Int, val markerLength: Int) {
  val indentStr = " ".repeat(indent)

  fun subItemIndent() = indent + markerLength

  fun subItem(markerLength: Int) = ListItemIndentInfo(subItemIndent(), markerLength)

  fun with(indent: Int = this.indent, markerLength: Int = this.markerLength) = ListItemIndentInfo(indent, markerLength)

  /**
   * Returns a replacement for the [line]'s indent, so that a [ListItemIndentInfo] on that line changes its indent
   * from [indent] to [newIndent]. Returns null if this [ListItemIndentInfo] had indent greater, that the given [line].
   * The returned replacement turns all tabs in the indent of the [line] into spaces.
   */
  fun changeLineIndent(line: Int, newIndent: Int, document: Document, file: PsiFile? = null): Replacement? {
    val newIndentInfo = with(indent = newIndent)
    val oldIndentRange = document.getLineIndentRange(line)

    val realIndent = document.getLineIndentSpaces(line, file)?.length
    val lineIsBlank = oldIndentRange.endOffset == document.getLineEndOffset(line)
    if (realIndent == null || realIndent < indent || lineIsBlank) {
      return null
    }

    val newFullIndent = newIndentInfo.indentStr + " ".repeat(realIndent - indent) // with tabs replaced by spaces
    return Replacement(oldIndentRange, newFullIndent)
  }
}
