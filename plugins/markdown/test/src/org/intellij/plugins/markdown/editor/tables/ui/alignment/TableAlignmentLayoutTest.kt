// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.tables.ui.alignment

import org.assertj.core.api.Assertions.assertThat
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableSeparatorRow.CellAlignment
import org.junit.jupiter.api.Test

class TableAlignmentLayoutTest {
  @Test
  fun `compute uses the widest segments and rightmost origin`() {
    val layout = TableAlignmentLayout.compute(
      rows = listOf(
        RowSegments(originX = 12, segmentWidths = listOf(40, 10)),
        RowSegments(originX = 0, segmentWidths = listOf(15, 60)),
      ),
      alignments = listOf(CellAlignment.NONE, CellAlignment.NONE),
    )

    assertThat(layout.columnWidths).containsExactly(40, 60)
    assertThat(layout.rows[0].originPad).isZero()
    assertThat(layout.rows[1].originPad).isEqualTo(12)
    assertThat(layout.rows[1].cells).containsExactly(CellPad(0, 25), CellPad(0, 0))
  }

  @Test
  fun `cell padding follows alignment`() {
    assertThat(pad(CellAlignment.NONE)).isEqualTo(CellPad(0, 30))
    assertThat(pad(CellAlignment.LEFT)).isEqualTo(CellPad(0, 30))
    assertThat(pad(CellAlignment.RIGHT)).isEqualTo(CellPad(30, 0))
    assertThat(pad(CellAlignment.CENTER)).isEqualTo(CellPad(15, 15))
    assertThat(TableAlignmentLayout.computeCellPad(45, 40, CellAlignment.CENTER)).isEqualTo(CellPad(2, 3))
  }

  @Test
  fun `ragged rows use only their declared segments`() {
    val layout = TableAlignmentLayout.compute(
      rows = listOf(
        RowSegments(0, listOf(40, 60, 80)),
        RowSegments(0, listOf(10, 20)),
      ),
      alignments = listOf(CellAlignment.RIGHT),
    )

    assertThat(layout.columnWidths).containsExactly(40, 60, 80)
    assertThat(layout.rows[1].cells).containsExactly(CellPad(30, 0), CellPad(0, 40))
  }

  @Test
  fun `empty and over-wide segments need no padding`() {
    assertThat(TableAlignmentLayout.compute(emptyList(), emptyList()).rows).isEmpty()
    assertThat(TableAlignmentLayout.computeCellPad(40, 40, CellAlignment.CENTER)).isEqualTo(CellPad.NONE)
    assertThat(TableAlignmentLayout.computeCellPad(40, 55, CellAlignment.RIGHT)).isEqualTo(CellPad.NONE)
  }

  private fun pad(alignment: CellAlignment): CellPad =
    TableAlignmentLayout.computeCellPad(columnWidth = 70, segmentWidth = 40, alignment)
}
