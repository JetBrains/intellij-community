// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.tables.ui.alignment

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.siblings
import com.intellij.psi.util.startOffset
import com.intellij.util.concurrency.annotations.RequiresEdt
import it.unimi.dsi.fastutil.ints.IntArrayList
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.hasCorrectBorders
import org.intellij.plugins.markdown.editor.tables.TableProps
import org.intellij.plugins.markdown.editor.tables.TableUtils
import org.intellij.plugins.markdown.editor.tables.TableUtils.calculateActualTextRange
import org.intellij.plugins.markdown.editor.tables.TableUtils.separatorRow
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableRow
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableSeparatorRow
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableSeparatorRow.CellAlignment
import org.intellij.plugins.markdown.lang.psi.util.hasType
import org.intellij.plugins.markdown.lang.supportsMarkdown
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Rectangle

/** Transparent inline padding owned by visual table alignment. */
@ApiStatus.Internal
class TablePadRenderer(var padWidth: Int) : EditorCustomElementRenderer {
  override fun calcWidthInPixels(inlay: Inlay<*>): Int = padWidth

  override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
  }
}

/** Owns the visual table-alignment padding in one editor. */
@ApiStatus.Internal
class MarkdownTableAlignmentModel(private val editor: Editor) : Disposable {
  // Ordered by marker offset for updateEditedCell's binary search.
  private val entries = ArrayList<TableEntry>()

  @RequiresEdt
  fun refreshVisible() {
    val visible = editor.calculateVisibleRange()
    val margin = visible.length
    val document = editor.document
    refresh(TextRange(
      (visible.startOffset - margin).coerceAtLeast(0),
      (visible.endOffset + margin).coerceAtMost(document.textLength),
    ))
  }

  /** Refreshes tables intersecting [range] after the document is committed. */
  @RequiresEdt
  fun refresh(range: TextRange) {
    if (!isMarkdownTableVisualAlignmentEnabled(editor)) {
      disposeAllPads()
      return
    }
    val project = editor.project ?: return
    val documentManager = PsiDocumentManager.getInstance(project)
    val document = editor.document
    if (document.textLength == 0) {
      disposeAllPads()
      return
    }
    check(documentManager.isCommitted(document)) {
      "Document must be committed before refreshing table alignment"
    }
    val file = documentManager.getPsiFile(document) ?: return
    if (!file.supportsMarkdown()) {
      return
    }
    val tables = SyntaxTraverser.psiTraverser(file)
      .onRange(range)
      .filter(MarkdownTable::class.java)
      .filter { it.isValid }
    val touched = HashSet<TableEntry>()
    for (table in tables) {
      touched.add(layoutTable(table))
    }
    dropOrphanedEntries(range, touched)
  }

  /** Re-pads the edited cell synchronously without shrinking its column. */
  @RequiresEdt
  fun updateEditedCell(event: DocumentEvent): Boolean {
    if (!isMarkdownTableVisualAlignmentEnabled(editor)) {
      return false
    }
    if (StringUtil.indexOf(event.oldFragment, '\n') >= 0 || StringUtil.indexOf(event.newFragment, '\n') >= 0) {
      entries.forEach { it.fastPath = null }
      return false
    }
    val document = editor.document
    val offset = event.offset
    val entry = findEntryAt(offset) ?: return false
    val fastPath = entry.fastPath ?: return false
    if (isHiddenByCollapsedRegion(offset, offset + event.newLength)) {
      return true
    }
    if (!TableUtils.isProbablyInsideTableCell(document, offset)) {
      return false
    }
    val line = document.getLineNumber(offset)
    val relativeLine = line - document.getLineNumber(entry.marker.startOffset)
    val rowIndex = fastPath.rowIndices.getOrNull(relativeLine)?.takeIf { it >= 0 } ?: return false
    val borderCount = fastPath.borderCounts.getOrNull(relativeLine)?.takeIf { it >= 2 } ?: return false
    val borders = scanBorders(line, borderCount)
    if (borders == null) {
      entry.fastPath = null
      return false
    }
    val borderIndex = borders.binarySearch(offset)
    val column = if (borderIndex >= 0) borderIndex - 1 else -borderIndex - 2
    if (column < 0 || column >= borders.size - 1 || column >= fastPath.columnWidths.size) {
      return false
    }
    val segmentWidth = measureRow(editor, intArrayOf(borders[column], borders[column + 1]))?.segmentWidths?.single() ?: return false
    val pad = TableAlignmentLayout.computeCellPad(
      fastPath.columnWidths[column],
      segmentWidth,
      fastPath.alignments.getOrElse(column) { CellAlignment.NONE },
    )
    setPad(entry, PadKey(rowIndex, column, PadSlot.LEAD), borders[column] + 1, pad.lead, relatesToPrecedingText = false)
    setPad(entry, PadKey(rowIndex, column, PadSlot.TAIL), borders[column + 1], pad.tail, relatesToPrecedingText = true)
    return true
  }

  @RequiresEdt
  fun disposeAllPads() {
    for (entry in entries) {
      entry.dispose()
    }
    entries.clear()
  }

  override fun dispose() {
    disposeAllPads()
  }

  private fun layoutTable(table: MarkdownTable): TableEntry {
    val entry = entryFor(table)
    if (!table.hasCorrectBorders()) {
      entry.disposePads()
      return entry
    }
    val rowElements = rowElementsOf(table)
    if (rowElements.size > MAX_ALIGNED_ROWS) {
      thisLogger().debug("Not aligning a table with ${rowElements.size} rows, over the $MAX_ALIGNED_ROWS row limit")
      entry.disposePads()
      return entry
    }
    val borderOffsetsByRow = ArrayList<IntArray>(rowElements.size)
    val measured = ArrayList<RowSegments>(rowElements.size)
    for (rowElement in rowElements) {
      val borders = borderOffsetsOf(rowElement)
      val segments = borders?.let { measureRow(editor, it) }
      if (segments == null) {
        entry.disposePads()
        return entry
      }
      borderOffsetsByRow.add(borders)
      measured.add(segments)
    }
    if (measured.isEmpty()) {
      entry.disposePads()
      return entry
    }
    val alignments = alignmentsOf(table)
    val layout = TableAlignmentLayout.compute(measured, alignments)
    val firstLine = editor.document.getLineNumber(table.textRange.startOffset)
    val relativeLines = IntArray(borderOffsetsByRow.size)
    var lastRelativeLine = -1
    for (index in borderOffsetsByRow.indices) {
      val line = editor.document.getLineNumber(borderOffsetsByRow[index].first()) - firstLine
      relativeLines[index] = line
      lastRelativeLine = maxOf(lastRelativeLine, line)
    }
    val borderCounts = IntArray(lastRelativeLine + 1) { -1 }
    val rowIndices = IntArray(borderCounts.size) { -1 }
    for ((index, line) in relativeLines.withIndex()) {
      borderCounts[line] = borderOffsetsByRow[index].size
      rowIndices[line] = index
    }
    applyPads(entry, borderOffsetsByRow, layout)
    entry.fastPath = FastPathState(borderCounts, rowIndices, alignments, layout.columnWidths)
    return entry
  }

  private fun isHiddenByCollapsedRegion(startOffset: Int, endOffset: Int): Boolean {
    return editor.foldingModel.getCollapsedRegionAtOffset(startOffset)?.endOffset?.let { it >= endOffset } == true
  }

  private fun scanBorders(line: Int, expectedCount: Int): IntArray? {
    val document = editor.document
    val text = document.immutableCharSequence
    val offsets = IntArray(expectedCount)
    var count = 0
    for (offset in document.getLineStartOffset(line) until document.getLineEndOffset(line)) {
      if (text[offset] == TableProps.SEPARATOR_CHAR) {
        if (count == expectedCount) return null
        offsets[count++] = offset
      }
    }
    return offsets.takeIf { count == expectedCount }
  }

  private fun applyPads(entry: TableEntry, borderOffsetsByRow: List<IntArray>, layout: TableLayout) {
    val obsolete = entry.pads.keys.toHashSet()
    fun apply(key: PadKey, offset: Int, width: Int, relatesToPrecedingText: Boolean) {
      obsolete.remove(key)
      setPad(entry, key, offset, width, relatesToPrecedingText)
    }

    for ((rowIndex, rowPads) in layout.rows.withIndex()) {
      val borders = borderOffsetsByRow[rowIndex]
      if (rowPads.originPad > 0) {
        apply(PadKey(rowIndex, ORIGIN_COLUMN, PadSlot.ORIGIN), borders.first(), rowPads.originPad, relatesToPrecedingText = false)
      }
      for ((column, pad) in rowPads.cells.withIndex()) {
        if (pad.lead > 0) {
          apply(PadKey(rowIndex, column, PadSlot.LEAD), borders[column] + 1, pad.lead, relatesToPrecedingText = false)
        }
        if (pad.tail > 0) {
          apply(PadKey(rowIndex, column, PadSlot.TAIL), borders[column + 1], pad.tail, relatesToPrecedingText = true)
        }
      }
    }
    for (key in obsolete) {
      entry.pads.remove(key)?.let(Disposer::dispose)
    }
  }

  private fun setPad(entry: TableEntry, key: PadKey, offset: Int, width: Int, relatesToPrecedingText: Boolean) {
    val existing = entry.pads[key]
    if (existing != null && (width <= 0 || !existing.isValid || existing.offset != offset)) {
      entry.pads.remove(key)
      Disposer.dispose(existing)
    }
    else if (existing != null) {
      if (existing.renderer.padWidth != width) {
        existing.renderer.padWidth = width
        existing.update()
      }
      return
    }
    if (width <= 0) {
      return
    }
    val properties = InlayProperties()
      .relatesToPrecedingText(relatesToPrecedingText)
      .priority(PAD_PRIORITY)
    val renderer = TablePadRenderer(width)
    val inlay = editor.inlayModel.addInlineElement(offset, properties, renderer)
    if (inlay == null) {
      thisLogger().debug("Editor refused an alignment pad at $offset")
      return
    }
    entry.pads[key] = inlay
  }

  private fun entryFor(table: MarkdownTable): TableEntry {
    val range = table.textRange
    val index = entries.binarySearchBy(range.startOffset) { it.marker.startOffset }
    if (index >= 0) {
      val existing = entries[index]
      if (existing.marker.endOffset != range.endOffset) {
        existing.marker.dispose()
        existing.marker = createMarker(range)
      }
      return existing
    }
    val entry = TableEntry(createMarker(range))
    entries.add(-index - 1, entry)
    return entry
  }

  private fun findEntryAt(offset: Int): TableEntry? {
    val index = entries.binarySearchBy(offset) { it.marker.startOffset }
    val entry = entries.getOrNull(if (index < 0) -index - 2 else index) ?: return null
    return entry.takeIf { it.fastPath != null && it.marker.isValid && offset <= it.marker.endOffset }
  }

  private fun createMarker(range: TextRange): RangeMarker {
    return editor.document.createRangeMarker(range.startOffset, range.endOffset, true).also { it.isGreedyToRight = true }
  }

  private fun dropOrphanedEntries(range: TextRange, touched: Set<TableEntry>) {
    var index = entries.binarySearchBy(range.startOffset) { it.marker.startOffset }
      .let { if (it < 0) -it - 1 else it }
    if (index > 0) index--
    while (index < entries.size) {
      val entry = entries[index]
      if (entry.marker.startOffset >= range.endOffset) break
      if (entry.marker.textRange.intersects(range) && entry !in touched) {
        entry.dispose()
        entries.removeAt(index)
      }
      else {
        index++
      }
    }
  }

  private fun rowElementsOf(table: MarkdownTable): List<PsiElement> {
    val separator = table.separatorRow ?: return emptyList()
    return table.getRows(true) + separator
  }

  private fun borderOffsetsOf(rowElement: PsiElement): IntArray? {
    val offsets = IntArrayList()
    when (rowElement) {
      is MarkdownTableRow -> rowElement.firstChild
        ?.siblings(forward = true, withSelf = true)
        ?.filter { it.hasType(MarkdownTokenTypes.TABLE_SEPARATOR) && it !is MarkdownTableSeparatorRow }
        ?.forEach { offsets.add(it.startOffset) }
      is MarkdownTableSeparatorRow -> {
        val range = rowElement.calculateActualTextRange()
        val text = editor.document.immutableCharSequence
        for (offset in range.startOffset until range.endOffset) {
          if (text[offset] == TableProps.SEPARATOR_CHAR) offsets.add(offset)
        }
      }
      else -> return null
    }
    return offsets.takeIf { it.size >= 2 }?.toIntArray()
  }

  private fun alignmentsOf(table: MarkdownTable): List<CellAlignment> {
    val separator = table.separatorRow ?: return emptyList()
    return separator.cellsRanges.indices.map { separator.getCellAlignment(it) }
  }

  private class TableEntry(var marker: RangeMarker) {
    val pads: MutableMap<PadKey, Inlay<TablePadRenderer>> = HashMap()

    var fastPath: FastPathState? = null

    fun disposePads() {
      pads.values.forEach(Disposer::dispose)
      pads.clear()
      fastPath = null
    }

    fun dispose() {
      disposePads()
      marker.dispose()
    }
  }

  /** Cached table geometry for keeping borders stable during edits before the full refresh. */
  private class FastPathState(
    val borderCounts: IntArray,
    val rowIndices: IntArray,
    val alignments: List<CellAlignment>,
    val columnWidths: List<Int>,
  )

  private enum class PadSlot { ORIGIN, LEAD, TAIL }

  private data class PadKey(val row: Int, val column: Int, val slot: PadSlot)

  companion object {
    const val MAX_ALIGNED_ROWS: Int = 500

    private const val ORIGIN_COLUMN = -1

    // Below the row bar at the first separator.
    private const val PAD_PRIORITY = -1000
  }
}
