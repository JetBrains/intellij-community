// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.inspections

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.tables.TableTestUtils
import org.junit.Test

class MarkdownIncorrectTableFormattingInspectionTest: LightPlatformCodeInsightFixture4TestCase() {
  private val description
    get() = MarkdownBundle.message("markdown.incorrect.table.formatting.inspection.description")

  @Test
  fun `shows inspection with incorrectly formatted cell`() {
    // language=Markdown
    val expected = """
    <weak-warning desc="$description">| none | none |
    |------|------|
    | some | some   |</weak-warning>
    """.trimIndent()
    doTest(expected)
  }

  @Test
  fun `shows inspection with incorrectly formatted header cell`() {
    // language=Markdown
    val expected = """
    <weak-warning desc="$description">| none   | none |
    |-------:|------|
    |   some | some |</weak-warning>
    """.trimIndent()
    doTest(expected)
  }

  @Test
  fun `shows inspection with incorrectly formatted separator`() {
    // language=Markdown
    val expected = """
    <weak-warning desc="$description">| none | none |
    |------|---|
    | some | some |</weak-warning>
    """.trimIndent()
    doTest(expected)
  }

  @Test
  fun `no inspections on table with correct formatting`() {
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
      myFixture.enableInspections(MarkdownIncorrectTableFormattingInspection())
      myFixture.checkHighlighting()
    }
  }
}
