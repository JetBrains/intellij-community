// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.inspections

import com.intellij.markdown.backend.inspections.MarkdownNoTableBordersInspection
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.intellij.plugins.markdown.MarkdownBundle
import org.junit.jupiter.api.Test


@Suppress("MarkdownIncorrectTableFormatting", "MarkdownNoTableBorders")
@TestApplication
class MarkdownNoTableBordersInspectionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private val description
    get() = MarkdownBundle.message("markdown.no.table.borders.inspection.description")

  @Test
  fun `shows inspection on table without borders`() {
    // language=Markdown
    val expected = """
    <warning descr="$description">none | none</warning>
    -----|---
    some | some
    """.trimIndent()
    doTest(expected)
  }

  @Test
  fun `show inspection on table with partial borders`() {
    // language=Markdown
    val expected = """
    <warning descr="$description">|none | none</warning>
    |-----|---
    some | some
    """.trimIndent()
    doTest(expected)
  }

  @Test
  fun `no inspections on table with correct borders`() {
    // language=Markdown
    val expected = """
    | none | none |
    |------|------|
    | some | some |
    """.trimIndent()
    doTest(expected)
  }

  @Test
  fun `no inspection for top level indented table`() {
    // language=Markdown
    val expected = """
       | none | none |
       |------|------|
       | some | some |

    trimIndent marker
    """.trimIndent()
    doTest(expected)
  }

  @Test
  fun `no inspection for indented table inside list`() {
    // language=Markdown
    val expected = """
    * Some list item with a table
      
      | none | none |
      |------|------|
      | some | some |
    """.trimIndent()
    doTest(expected)
  }

  @Test
  fun `no inspection for table inside block quote`() {
    // language=Markdown
    val expected = """
    > | none | none |
    > |------|------|
    > | some | some |
    """.trimIndent()
    doTest(expected)
  }

  private fun doTest(expected: String) {
    myFixture.configureByText("some.md", expected)
    myFixture.enableInspections(MarkdownNoTableBordersInspection())
    myFixture.checkHighlighting()
  }
}
