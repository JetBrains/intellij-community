// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.inspections

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.tables.TableTestUtils
import org.junit.Test

class MarkdownNoTableBordersInspectionQuickFixTest: LightPlatformCodeInsightFixture4TestCase() {
  @Test
  fun `works on table without borders`() {
    // language=Markdown
    val before = """
    none | none
    -----|---
    some | some
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
  fun `works on table with partial borders`() {
    // language=Markdown
    val before = """
    |none | none
    -----|---|
    |some | some
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
  fun `works on table without borders and empty last cell`() {
    // language=Markdown
    val before = """
    none | none
    -----|---
    some |
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none |
    |------|------|
    | some |      |
    """.trimIndent()
    doTest(before, after)
  }

  private fun doTest(content: String, after: String) {
    TableTestUtils.runWithChangedSettings(myFixture.project) {
      myFixture.configureByText("some.md", content)
      myFixture.enableInspections(MarkdownNoTableBordersInspection())
      val targetText = MarkdownBundle.message("markdown.fix.table.borders.intention.text")
      val fix = myFixture.getAllQuickFixes().find { it.text == targetText }
      assertNotNull(fix)
      myFixture.launchAction(fix!!)
      myFixture.checkResult(after)
    }
  }
}
