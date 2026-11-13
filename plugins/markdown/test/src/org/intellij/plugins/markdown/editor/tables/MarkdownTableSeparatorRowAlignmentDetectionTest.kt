// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.openapi.application.readAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.intellij.plugins.markdown.editor.tables.TableUtils.separatorRow
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableSeparatorRow
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@TestApplication
@Suppress("MarkdownIncorrectTableFormatting")
class MarkdownTableSeparatorRowAlignmentDetectionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  private fun doTest(content: String, expectedAlignments: List<MarkdownTableSeparatorRow.CellAlignment>): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", content)
    readAction {
      val table = myFixture.file.firstChild as? MarkdownTable
      assertNotNull(table)
      requireNotNull(table)
      val separator = requireNotNull(table.separatorRow)
      val alignments = separator.cellsRanges.indices.map { separator.getCellAlignment(it) }
      assertThat(alignments).containsExactlyElementsOf(expectedAlignments)
    }
  }
}
