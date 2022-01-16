// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.inspections

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.tables.TableTestUtils
import org.junit.Test


class MarkdownNoTableBordersInspectionTest: LightPlatformCodeInsightFixture4TestCase() {
  private val description
    get() = MarkdownBundle.message("markdown.no.table.borders.inspection.description")

  @Test
  fun `shows inspection on table without borders`() {
    // language=Markdown
    val expected = """
    <error descr="$description">none | none
    -----|---
    some | some</error>
    """.trimIndent()
    doTest(expected)
  }

  @Test
  fun `show inspection on table with partial borders`() {
    // language=Markdown
    val expected = """
    <error descr="$description">|none | none
    |-----|---
    some | some</error>
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

  private fun doTest(expected: String) {
    TableTestUtils.runWithChangedSettings(myFixture.project) {
      myFixture.configureByText("some.md", expected)
      myFixture.enableInspections(MarkdownNoTableBordersInspection())
      myFixture.checkHighlighting()
    }
  }
}
