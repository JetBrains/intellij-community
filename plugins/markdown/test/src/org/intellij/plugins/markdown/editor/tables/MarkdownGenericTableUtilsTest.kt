// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.validateColumnAlignment
import org.intellij.plugins.markdown.editor.tables.TableUtils.columnsIndices
import org.intellij.plugins.markdown.editor.tables.TableUtils.separatorRow

class MarkdownGenericTableUtilsTest: LightPlatformCodeInsightTestCase() {
  fun `test findCellIndex works fine`() {
    // language=Markdown
    val content = """
    | some | stuff | more |
    |------|-------|:----:|
    | a    |  asd  | 56   |
    """.trimIndent()
    configureFromFileText("some.md", content)
    val offsetInHeaderCell = 4
    val offsetInSeparatorCell = 34
    val offsetInContentCell = 68
    assertEquals(0, TableUtils.findCellIndex(file, offsetInHeaderCell))
    assertEquals(1, TableUtils.findCellIndex(file, offsetInSeparatorCell))
    assertEquals(2, TableUtils.findCellIndex(file, offsetInContentCell))
  }

  fun `test separator cells ranges correct`() {
    // language=Markdown
    val content = """
    | some | stuff  | more |
    |------|:-------|:----:|
    | a    | asd    | 56   |
    """.trimIndent()
    configureFromFileText("some.md", content)
    val table = TableUtils.findTable(file, 0)
    val ranges = requireNotNull(table?.separatorRow).cellsRanges
    val actualContents = ranges.map { editor.document.getText(it) }
    assertOrderedEquals(actualContents, listOf("------", ":-------", ":----:"))
  }

  fun `test hasValidAlignment detects valid alignments`() {
    // language=Markdown
    val content = """
    | none | left   | center |   right |  center uneven  | 
    |------|:-------|:------:|--------:|:---------------:|
    |   a  | asd    |   56   |      56 |  cell content   |
    """.trimIndent()
    configureFromFileText("some.md", content)
    val table = TableUtils.findTable(file, 0)!!
    for (columnIndex in table.columnsIndices) {
      assertTrue("column $columnIndex should have valid alignment", table.validateColumnAlignment(columnIndex))
    }
  }

  fun `test hasValidAlignment detects invalid alignments`() {
    // language=Markdown
    val content = """
    | none | left   | center |   right |  center uneven  | 
    |------|:-------|:------:|--------:|:---------------:|
    |   a  |   sds  | ss     |   566   |   cell content  |
    """.trimIndent()
    configureFromFileText("some.md", content)
    val table = TableUtils.findTable(file, 0)!!
    assertTrue("column 0 should still have valid alignment", table.validateColumnAlignment(0))
    for (columnIndex in table.columnsIndices.drop(1)) {
      assertFalse("column $columnIndex should have invalid alignment", table.validateColumnAlignment(columnIndex))
    }
  }
}
