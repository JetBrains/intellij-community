// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.editor.tables.TableUtils.separatorRow
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableSeparatorRow

class MarkdownTableSeparatorRowAlignmentDetectionTest: LightPlatformCodeInsightTestCase() {
  fun `test all none`() {
    // language=Markdown
    doTest(
      """
      | none | none |
      |------|------|
      |  a   | asd  |
      """.trimIndent(),
      listOf(
        MarkdownTableSeparatorRow.CellAlignment.NONE,
        MarkdownTableSeparatorRow.CellAlignment.NONE
      )
    )
  }

  fun `test first left`() {
    // language=Markdown
    doTest(
      """
      | left | none |
      |:-----|------|
      |  a   | asd  |
      """.trimIndent(),
      listOf(
        MarkdownTableSeparatorRow.CellAlignment.LEFT,
        MarkdownTableSeparatorRow.CellAlignment.NONE
      )
    )
  }

  fun `test first left second right`() {
    // language=Markdown
    doTest(
      """
      | left | right |
      |:-----|------:|
      |  a   | asd   |
      """.trimIndent(),
      listOf(
        MarkdownTableSeparatorRow.CellAlignment.LEFT,
        MarkdownTableSeparatorRow.CellAlignment.RIGHT
      )
    )
  }

  fun `test first center second left`() {
    // language=Markdown
    doTest(
      """
      | center | left |
      |:------:|:-----|
      |    a   | asd  |
      """.trimIndent(),
      listOf(
        MarkdownTableSeparatorRow.CellAlignment.CENTER,
        MarkdownTableSeparatorRow.CellAlignment.LEFT
      )
    )
  }

  fun `test all with spaces`() {
    // language=Markdown
    doTest(
      """
      | none | left | right | center |
      | ---  | :--- | ---:  |  :---: |
      |  0   |   1  |   2   |    3   |
      """.trimIndent(),
      listOf(
        MarkdownTableSeparatorRow.CellAlignment.NONE,
        MarkdownTableSeparatorRow.CellAlignment.LEFT,
        MarkdownTableSeparatorRow.CellAlignment.RIGHT,
        MarkdownTableSeparatorRow.CellAlignment.CENTER
      )
    )
  }

  fun `test many columns`() {
    // language=Markdown
    doTest(
      """
      | center   | right  |  left |  center                                 | none   |
      |:---:|---:|:---|           :-------------------:    |  ---|
      |  cell content   |  cell content  | cell content | cell content | cell content |
      |  cell content   |  cell content  | cell content | cell content | cell content |
      """.trimIndent(),
      listOf(
        MarkdownTableSeparatorRow.CellAlignment.CENTER,
        MarkdownTableSeparatorRow.CellAlignment.RIGHT,
        MarkdownTableSeparatorRow.CellAlignment.LEFT,
        MarkdownTableSeparatorRow.CellAlignment.CENTER,
        MarkdownTableSeparatorRow.CellAlignment.NONE
      )
    )
  }

  private fun doTest(content: String, expectedAlignments: List<MarkdownTableSeparatorRow.CellAlignment>) {
    configureFromFileText("some.md", content)
    val table = file.firstChild?.firstChild as? MarkdownTable
    assertNotNull(table)
    requireNotNull(table)
    val separator = requireNotNull(table.separatorRow)
    val alignments = separator.cellsRanges.indices.map { separator.getCellAlignment(it) }
    assertOrderedEquals(alignments, expectedAlignments)
  }
}
