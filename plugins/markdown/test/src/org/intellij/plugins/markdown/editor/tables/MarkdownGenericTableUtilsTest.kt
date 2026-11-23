// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.openapi.application.readAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.validateColumnAlignment
import org.intellij.plugins.markdown.editor.tables.TableUtils.columnsIndices
import org.intellij.plugins.markdown.editor.tables.TableUtils.separatorRow
import org.intellij.plugins.markdown.lang.formatter.settings.TableStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
@Suppress("MarkdownIncorrectTableFormatting")
class MarkdownGenericTableUtilsTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test findCellIndex works fine`(): Unit = timeoutRunBlocking {
    // language=Markdown
    val content = """
    | some | stuff | more |
    |------|-------|:----:|
    | a    |  asd  | 56   |
    """.trimIndent()
    myFixture.configureByText("some.md", content)
    val offsetInHeaderCell = 4
    val offsetInSeparatorCell = 34
    val offsetInContentCell = 68
    readAction {
      assertEquals(0, TableUtils.findCellIndex(myFixture.file, offsetInHeaderCell))
      assertEquals(1, TableUtils.findCellIndex(myFixture.file, offsetInSeparatorCell))
      assertEquals(2, TableUtils.findCellIndex(myFixture.file, offsetInContentCell))
    }
  }

  @Test
  fun `test separator cells ranges correct`(): Unit = timeoutRunBlocking {
    // language=Markdown
    val content = """
    | some | stuff  | more |
    |------|:-------|:----:|
    | a    | asd    | 56   |
    """.trimIndent()
    myFixture.configureByText("some.md", content)
    readAction {
      val table = TableUtils.findTable(myFixture.file, 0)
      val ranges = requireNotNull(table?.separatorRow).cellsRanges
      val actualContents = ranges.map { myFixture.editor.document.getText(it) }
      assertThat(actualContents).containsExactly("------", ":-------", ":----:")
    }
  }

  @Test
  fun `test hasValidAlignment detects valid alignments`(): Unit = timeoutRunBlocking {
    // language=Markdown
    val content = """
    | none | left   | center |   right |  center uneven  |
    |------|:-------|:------:|--------:|:---------------:|
    |   a  | asd    |   56   |      56 |  cell content   |
    """.trimIndent()
    myFixture.configureByText("some.md", content)
    readAction {
      val table = TableUtils.findTable(myFixture.file, 0)!!
      for (columnIndex in table.columnsIndices) {
        assertTrue(table.validateColumnAlignment(columnIndex, TableStyle.ALIGNED), "column $columnIndex should have valid alignment")
      }
    }
  }

  @Test
  fun `test hasValidAlignment detects invalid alignments`(): Unit = timeoutRunBlocking {
    // language=Markdown
    val content = """
    | none | left   | center |   right |  center uneven  |
    |------|:-------|:------:|--------:|:---------------:|
    |   a  |   sds  | ss     |   566   |   cell content  |
    """.trimIndent()
    myFixture.configureByText("some.md", content)
    readAction {
      val table = TableUtils.findTable(myFixture.file, 0)!!
      assertTrue(table.validateColumnAlignment(0, TableStyle.ALIGNED), "column 0 should still have valid alignment")
      for (columnIndex in table.columnsIndices.drop(1)) {
        assertFalse(table.validateColumnAlignment(columnIndex, TableStyle.ALIGNED), "column $columnIndex should have invalid alignment")
      }
    }
  }

  @Test
  fun `test build empty state respects table style`() {
    assertEquals(
      "|   |   |\n|---|---|\n|   |   |\n",
      buildEmptyTable(contentRows = 1, columns = 2, cellWidth = 3, tableStyle = TableStyle.ALIGNED)
    )
    assertEquals(
      "| | |\n| --- | --- |\n| | |\n",
      buildEmptyTable(contentRows = 1, columns = 2, cellWidth = 3, tableStyle = TableStyle.COMPACT)
    )
    assertEquals(
      "|||\n|---|---|\n|||\n",
      buildEmptyTable(contentRows = 1, columns = 2, cellWidth = 3, tableStyle = TableStyle.TIGHT)
    )
  }
}
