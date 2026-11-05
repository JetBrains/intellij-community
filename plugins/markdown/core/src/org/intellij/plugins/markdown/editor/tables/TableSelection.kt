// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.SmartPointerManager
import org.intellij.plugins.markdown.editor.tables.TableUtils.separatorRow
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import java.awt.event.MouseEvent

internal enum class TableSelectionMode {
  REPLACE,
  TOGGLE,
  RANGE
}

internal enum class TableSelectionAxis {
  ROW,
  COLUMN
}

private data class TableSelectionState(
  val table: SmartPsiElementPointer<MarkdownTable>,
  val axis: TableSelectionAxis,
  val anchorIndex: Int,
  val selectedIndices: Set<Int>,
  val ranges: List<TextRange>
)

private val TABLE_SELECTION_STATE = Key.create<TableSelectionState>("MarkdownTableSelectionState")
private val TABLE_SELECTION_STATE_ON_MOUSE_PRESS = Key.create<TableSelectionState>("MarkdownTableSelectionStateOnMousePress")

internal fun MouseEvent.toTableSelectionMode(): TableSelectionMode = when {
  isShiftDown -> TableSelectionMode.RANGE
  isMacControlClick() -> TableSelectionMode.REPLACE
  ClientSystemInfo.isMac() && isMetaDown || !ClientSystemInfo.isMac() && isControlDown -> TableSelectionMode.TOGGLE
  else -> TableSelectionMode.REPLACE
}

internal fun MouseEvent.isMacControlClick(): Boolean = ClientSystemInfo.isMac() && isControlDown

internal fun Editor.prepareTableSelectionForMousePress(table: MarkdownTable, axis: TableSelectionAxis, mode: TableSelectionMode) {
  val currentRanges = getSelectionRanges()
  val previous = getUserData(TABLE_SELECTION_STATE)?.takeIf {
    mode != TableSelectionMode.REPLACE && it.ranges == currentRanges && it.belongsTo(table, axis)
  }
  putUserData(TABLE_SELECTION_STATE_ON_MOUSE_PRESS, previous)
}

private fun TableSelectionState.belongsTo(table: MarkdownTable, axis: TableSelectionAxis): Boolean {
  return this.axis == axis && runReadActionBlocking {
    this.table.element?.let { previousTable -> previousTable.isValid && previousTable.isEquivalentTo(table) } == true
  }
}

internal fun Editor.selectTableParts(
  table: MarkdownTable,
  axis: TableSelectionAxis,
  index: Int,
  mode: TableSelectionMode,
  rangesForIndices: (Set<Int>) -> List<TextRange>
) {
  val currentRanges = getSelectionRanges()
  val previous = getUserData(TABLE_SELECTION_STATE)?.takeIf {
    it.ranges == currentRanges && it.belongsTo(table, axis)
  } ?: getUserData(TABLE_SELECTION_STATE_ON_MOUSE_PRESS)?.takeIf { mode != TableSelectionMode.REPLACE && it.belongsTo(table, axis) }
  putUserData(TABLE_SELECTION_STATE_ON_MOUSE_PRESS, null)
  val anchorIndex = when (mode) {
    TableSelectionMode.REPLACE, TableSelectionMode.TOGGLE -> index
    TableSelectionMode.RANGE -> previous?.anchorIndex ?: index
  }
  val selectedIndices = when (mode) {
    TableSelectionMode.REPLACE -> setOf(index)
    TableSelectionMode.TOGGLE -> {
      val previousIndices = previous?.selectedIndices.orEmpty()
      if (index in previousIndices) previousIndices - index else previousIndices + index
    }
    TableSelectionMode.RANGE -> (minOf(anchorIndex, index)..maxOf(anchorIndex, index)).toSet()
  }
  replaceSelections(rangesForIndices(selectedIndices))
  val tablePointer = previous?.table ?: runReadActionBlocking { SmartPointerManager.createPointer(table) }
  putUserData(TABLE_SELECTION_STATE, TableSelectionState(tablePointer, axis, anchorIndex, selectedIndices, getSelectionRanges()))
}

private fun Editor.replaceSelections(ranges: List<TextRange>) {
  val caretModel = caretModel
  caretModel.removeSecondaryCarets()
  val orderedRanges = ranges.sortedWith(compareBy({ it.startOffset }, { it.endOffset }))
  if (orderedRanges.isEmpty()) {
    caretModel.currentCaret.removeSelection()
    return
  }
  val firstRange = orderedRanges.first()
  caretModel.currentCaret.apply {
    moveToOffset(firstRange.startOffset)
    setSelection(firstRange.startOffset, firstRange.endOffset)
  }
  for (range in orderedRanges.drop(1)) {
    val caret = caretModel.addCaret(offsetToVisualPosition(range.startOffset)) ?: continue
    caret.setSelection(range.startOffset, range.endOffset)
  }
}

private fun Editor.getSelectionRanges(): List<TextRange> {
  return caretModel.allCarets.asSequence()
    .filter { it.hasSelection() }
    .map { TextRange(it.selectionStart, it.selectionEnd) }
    .sortedWith(compareBy({ it.startOffset }, { it.endOffset }))
    .toList()
}

internal fun Collection<Int>.toContiguousRanges(): List<IntRange> {
  val indices = sorted()
  if (indices.isEmpty()) return emptyList()
  val ranges = ArrayList<IntRange>()
  var start = indices.first()
  var end = start
  for (index in indices.drop(1)) {
    if (index == end + 1) {
      end = index
    }
    else {
      ranges.add(start..end)
      start = index
      end = index
    }
  }
  ranges.add(start..end)
  return ranges
}

internal fun MarkdownTable.selectRow(
  editor: Editor,
  row: PsiElement,
  mode: TableSelectionMode = TableSelectionMode.REPLACE
) {
  val rows = buildList<PsiElement> {
    addAll(getRows(true))
    separatorRow?.let(::add)
  }.sortedBy { it.textRange.startOffset }
  val index = rows.indexOf(row)
  if (index < 0) return
  editor.selectTableParts(this, TableSelectionAxis.ROW, index, mode) { indices ->
    indices.toContiguousRanges().mapNotNull { range ->
      val first = rows.getOrNull(range.first) ?: return@mapNotNull null
      val last = rows.getOrNull(range.last) ?: return@mapNotNull null
      TextRange(first.textRange.startOffset, last.textRange.endOffset)
    }
  }
}
