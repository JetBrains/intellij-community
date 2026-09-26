// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.tables.ui.alignment

import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableSeparatorRow.CellAlignment
import org.jetbrains.annotations.ApiStatus

/** Rendered row geometry without alignment padding. */
@ApiStatus.Internal
data class RowSegments(val originX: Int, val segmentWidths: List<Int>)

/** Pixel padding at both sides of a cell. */
@ApiStatus.Internal
data class CellPad(val lead: Int, val tail: Int) {
  companion object {
    @JvmField
    val NONE: CellPad = CellPad(lead = 0, tail = 0)
  }
}

/** [originPad] precedes the first separator; [cells] contains one entry per present segment. */
@ApiStatus.Internal
data class RowPads(val originPad: Int, val cells: List<CellPad>)

/** Padding and target geometry for a table. */
@ApiStatus.Internal
data class TableLayout(val columnWidths: List<Int>, val rows: List<RowPads>)

/** Computes padding that aligns row separators. */
@ApiStatus.Internal
object TableAlignmentLayout {
  fun compute(rows: List<RowSegments>, alignments: List<CellAlignment>): TableLayout {
    if (rows.isEmpty()) {
      return TableLayout(columnWidths = emptyList(), rows = emptyList())
    }
    val originX = rows.maxOf { it.originX }
    val columnCount = rows.maxOf { it.segmentWidths.size }
    val columnWidths = (0 until columnCount).map { column ->
      rows.maxOf { it.segmentWidths.getOrElse(column) { 0 } }
    }
    val rowPads = rows.map { row ->
      RowPads(
        originPad = originX - row.originX,
        cells = row.segmentWidths.mapIndexed { column, segmentWidth ->
          computeCellPad(columnWidths[column], segmentWidth, alignments.getOrElse(column) { CellAlignment.NONE })
        }
      )
    }
    return TableLayout(columnWidths, rowPads)
  }

  fun computeCellPad(columnWidth: Int, segmentWidth: Int, alignment: CellAlignment): CellPad {
    val residual = columnWidth - segmentWidth
    if (residual <= 0) {
      return CellPad.NONE
    }
    return when (alignment) {
      CellAlignment.RIGHT -> CellPad(lead = residual, tail = 0)
      // Same bias as TableModificationUtils.buildRealignedCellContent: an odd pixel goes to the right.
      CellAlignment.CENTER -> CellPad(lead = residual / 2, tail = residual - residual / 2)
      CellAlignment.LEFT, CellAlignment.NONE -> CellPad(lead = 0, tail = residual)
    }
  }
}
