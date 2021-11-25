// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parents
import com.intellij.psi.util.siblings
import com.intellij.refactoring.suggested.startOffset
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableCellImpl
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableImpl
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableRowImpl
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableSeparatorRow
import org.intellij.plugins.markdown.util.hasType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
object TableUtils {
  @JvmStatic
  fun findCell(file: PsiFile, offset: Int): MarkdownTableCellImpl? {
    val element = PsiUtilCore.getElementAtOffset(file, offset)
    if (element.hasType(MarkdownTokenTypes.TABLE_SEPARATOR) && element !is MarkdownTableSeparatorRow &&
        element.text == TableProps.SEPARATOR_CHAR.toString()) {
      return element.prevSibling as? MarkdownTableCellImpl
    }
    return findCell(element)
  }

  @JvmStatic
  fun findCell(element: PsiElement): MarkdownTableCellImpl? {
    return element.parentOfType(withSelf = true)
  }

  @JvmStatic
  fun findTable(file: PsiFile, offset: Int): MarkdownTableImpl? {
    val element = PsiUtilCore.getElementAtOffset(file, offset)
    return findTable(element)
  }

  @JvmStatic
  fun findTable(element: PsiElement): MarkdownTableImpl? {
    return element.parentOfType(withSelf = true)
  }

  @JvmStatic
  fun findSeparatorRow(file: PsiFile, offset: Int): MarkdownTableSeparatorRow? {
    val element = PsiUtilCore.getElementAtOffset(file, offset)
    return findSeparatorRow(element)
  }

  @JvmStatic
  fun findSeparatorRow(element: PsiElement): MarkdownTableSeparatorRow? {
    return element.parentOfType(withSelf = true)
  }

  @JvmStatic
  fun findRow(file: PsiFile, offset: Int): MarkdownTableRowImpl? {
    val element = PsiUtilCore.getElementAtOffset(file, offset)
    return findRow(element)
  }

  @JvmStatic
  fun findRow(element: PsiElement): MarkdownTableRowImpl? {
    return element.parentOfType(withSelf = true)
  }

  @JvmStatic
  fun findRowOrSeparator(file: PsiFile, offset: Int): PsiElement? {
    val row = findRow(file, offset)
    return row ?: findSeparatorRow(file, offset)
  }

  @JvmStatic
  fun findRowOrSeparator(element: PsiElement): PsiElement? {
    return findRow(element) ?: findSeparatorRow(element)
  }

  /**
   * Find cell index for content or separator cells.
   */
  @JvmStatic
  fun findCellIndex(file: PsiFile, offset: Int): Int? {
    val element = PsiUtilCore.getElementAtOffset(file, offset)
    if (element.hasType(MarkdownTokenTypes.TABLE_SEPARATOR) && element !is MarkdownTableSeparatorRow &&
        element.text == TableProps.SEPARATOR_CHAR.toString()) {
      return (element.prevSibling as? MarkdownTableCellImpl)?.columnIndex
    }
    val parent = element.parents(withSelf = true).find {
      it.hasType(MarkdownElementTypes.TABLE_CELL) || it is MarkdownTableSeparatorRow
    }
    return when (parent) {
      is MarkdownTableSeparatorRow -> parent.getColumnIndexFromOffset(offset)
      is MarkdownTableCellImpl -> parent.columnIndex
      else -> null
    }
  }

  fun MarkdownTableImpl.getColumnCells(index: Int, withHeader: Boolean = true): List<MarkdownTableCellImpl> {
    return getRows(withHeader).mapNotNull { it.getCell(index) }
  }

  fun MarkdownTableImpl.getColumnTextRanges(index: Int): List<TextRange> {
    val cells = getColumnCells(index, withHeader = true).map { it.textRange }
    val result = ArrayList<TextRange>(cells.size + 1)
    result.addAll(cells)
    separatorRow?.let { result.add(it.textRange) }
    return result
  }

  val MarkdownTableImpl.separatorRow: MarkdownTableSeparatorRow?
    get() = firstChild.siblings(forward = true, withSelf = true).filterIsInstance<MarkdownTableSeparatorRow>().firstOrNull()

  val MarkdownTableRowImpl.isHeaderRow
    get() = siblings(forward = false, withSelf = false).find { it is MarkdownTableRowImpl } == null

  val MarkdownTableRowImpl.isLast
    get() = siblings(forward = true, withSelf = false).find { it is MarkdownTableRowImpl } == null

  val MarkdownTableRowImpl.columnsCount
    get() = firstChild?.siblings(forward = true, withSelf = true)?.count { it is MarkdownTableCellImpl } ?: 0

  val MarkdownTableImpl.columnsCount
    get() = headerRow?.columnsCount ?: 0

  val MarkdownTableImpl.columnsIndices
    get() = 0 until columnsCount

  val MarkdownTableRowImpl.columnsIndices
    get() = 0 until columnsCount

  fun MarkdownTableImpl.getColumnAlignment(columnIndex: Int): MarkdownTableSeparatorRow.CellAlignment {
    return separatorRow?.getCellAlignment(columnIndex)!!
  }

  val MarkdownTableCellImpl.firstNonWhitespaceOffset
    get() = startOffset + text.indexOfFirst { it != ' ' }.coerceAtLeast(0)

  val MarkdownTableCellImpl.lastNonWhitespaceOffset
    get() = startOffset + text.indexOfLast { it != ' ' }.coerceAtLeast(0)

  @JvmStatic
  fun isProbablyInsideTableCell(document: Document, caretOffset: Int): Boolean {
    if (caretOffset == 0) {
      return false
    }
    val text = document.charsSequence
    val lineNumber = document.getLineNumber(caretOffset)
    val lineStartOffset = document.getLineStartOffset(lineNumber)
    val lineEndOffset = document.getLineEndOffset(lineNumber)
    var leftBarFound = false
    for (offset in (caretOffset - 1) downTo lineStartOffset) {
      if (text[offset] == TableProps.SEPARATOR_CHAR) {
        leftBarFound = true
        break
      }
    }
    for (offset in caretOffset until lineEndOffset) {
      if (text[offset] == TableProps.SEPARATOR_CHAR) {
        return leftBarFound
      }
    }
    return false
  }
}
