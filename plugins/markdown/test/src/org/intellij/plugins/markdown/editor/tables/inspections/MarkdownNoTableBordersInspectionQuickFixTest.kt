// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.inspections

import com.intellij.codeInspection.InspectionsBundle
import com.intellij.markdown.backend.inspections.MarkdownNoTableBordersInspection
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.intellij.plugins.markdown.MarkdownBundle
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@Suppress("MarkdownIncorrectTableFormatting", "MarkdownNoTableBorders")
@TestApplication
class MarkdownNoTableBordersInspectionQuickFixTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

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

  @Test
  fun `works on table inside blockquote with missing trailing separator`() {
    // language=Markdown
    val before = """
    > |     |     |
    > |-----|-----<caret>
    > |     |     |
    """.trimIndent()
    // language=Markdown
    val after = """
    > |     |     |
    > |-----|-----|
    > |     |     |
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `works from any table row`() {
    // language=Markdown
    val before = """
    none | none
    -----|---
    some | some<caret>
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none |
    |------|------|
    | some | some |
    """.trimIndent()
    myFixture.configureByText("some.md", before)
    val targetText = MarkdownBundle.message("markdown.fix.table.borders.intention.text")
    val fix = myFixture.getAvailableIntention(targetText)
    assertNotNull(fix)
    myFixture.launchAction(fix!!)
    myFixture.checkResult(after)
  }

  @Test
  fun `applies fix to the whole file`() {
    // language=Markdown
    val before = """
    none | none
    -----|---
    some | some

    other | table
    ------|---
    value | value
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none |
    |------|------|
    | some | some |

    | other | table |
    |-------|-------|
    | value | value |
    """.trimIndent()
    myFixture.configureByText("some.md", before)
    val inspection = MarkdownNoTableBordersInspection()
    myFixture.enableInspections(inspection)
    val targetText = InspectionsBundle.message("fix.all.inspection.problems.in.file", inspection.displayName)
    myFixture.launchAction(myFixture.findSingleIntention(targetText))
    myFixture.checkResult(after)
  }

  private fun doTest(content: String, after: String) {
    myFixture.configureByText("some.md", content)
    myFixture.enableInspections(MarkdownNoTableBordersInspection())
    val targetText = MarkdownBundle.message("markdown.fix.table.borders.intention.text")
    val fix = myFixture.getAllQuickFixes().find { it.text == targetText }
    assertNotNull(fix)
    myFixture.launchAction(fix!!)
    myFixture.checkResult(after)
  }
}
