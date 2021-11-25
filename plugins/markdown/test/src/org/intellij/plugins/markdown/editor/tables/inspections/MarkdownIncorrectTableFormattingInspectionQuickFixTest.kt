// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.inspections

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.tables.TableTestUtils
import org.junit.Test

/**
 * These are just sanity checks, since actual tests for reformatting are in the other files.
 */
class MarkdownIncorrectTableFormattingInspectionQuickFixTest: LightPlatformCodeInsightFixture4TestCase() {
  @Test
  fun `works with incorrectly formatted cell`() {
    // language=Markdown
    val before = """
    | none | none |
    |------|------|
    | some | some   |
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none |
    |------|------|
    | some | some |
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `works with incorrectly formatted header cell`() {
    // language=Markdown
    val before = """
    | none   | none |
    |-------:|------|
    |   some | some |
    | some content | some |
    """.trimIndent()
    // language=Markdown
    val after = """
    |         none | none |
    |-------------:|------|
    |         some | some |
    | some content | some |
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `works with incorrectly formatted separator`() {
    // language=Markdown
    val before = """
    | none | none |
    |------|---|
    | some | some |
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none |
    |------|------|
    | some | some |
    """.trimIndent()
    doTest(before, after)
  }

  private fun doTest(content: String, after: String) {
    TableTestUtils.runWithChangedSettings(myFixture.project) {
      myFixture.configureByText("some.md", content)
      myFixture.enableInspections(MarkdownIncorrectTableFormattingInspection())
      val targetText = MarkdownBundle.message("markdown.reformat.table.intention.text")
      val fix = myFixture.getAllQuickFixes().find { it.text == targetText }
      assertNotNull(fix)
      myFixture.launchAction(fix!!)
      myFixture.checkResult(after)
    }
  }
}
