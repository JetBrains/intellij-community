// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.inspections

import com.intellij.codeInspection.InspectionsBundle
import com.intellij.idea.TestFor
import com.intellij.markdown.backend.inspections.MarkdownIncorrectTableFormattingInspection
import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.tables.withTableStyle
import org.intellij.plugins.markdown.lang.formatter.settings.TableStyle
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * These are just sanity checks, since actual tests for reformatting are in the other files.
 */
@Suppress("MarkdownIncorrectTableFormatting")
@TestApplication
class MarkdownIncorrectTableFormattingInspectionQuickFixTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private val reformatIntentionFixText
    get() = MarkdownBundle.message("markdown.reformat.table.intention.text")

  private val fixCellAlignmentIntentionText
    get() = MarkdownBundle.message("markdown.fix.cell.alignment.intention.text")

  @Test
  fun `reformat intention is available without inspection`() {
    myFixture.configureByText("some.md", "| first | second |\n|---|---|")
    assertNotNull(myFixture.availableIntentions.find { it.text == reformatIntentionFixText })
  }

  @Test
  fun `reformat intention is unavailable for nicely formatted table`() {
    myFixture.configureByText("some.md", "| first | second |\n| ----------- | ----------- |")
    assertNull(myFixture.availableIntentions.find { it.text == reformatIntentionFixText })
  }

  @Test
  fun `fix cell alignment intention is available without inspection`() {
    myFixture.configureByText("some.md", "| first |\n|------:|\n| some  <caret>|")
    assertNotNull(myFixture.availableIntentions.find { it.text == fixCellAlignmentIntentionText })
  }

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

  @TestFor(issues = ["IDEA-305781"])
  @Test
  fun `apply fix to whole file`() {
    // language=Markdown
    val before = """
    | first | table |
    |------|---|
    | some | some |
    
    | second | table |
    |------|---|
    | some | some |
    """.trimIndent()
    // language=Markdown
    val after = """
    | first | table |
    |-------|-------|
    | some  | some  |
    
    | second | table |
    |--------|-------|
    | some   | some  |
    """.trimIndent()
    myFixture.configureByText("some.md", before)
    val inspection = MarkdownIncorrectTableFormattingInspection()
    myFixture.enableInspections(inspection)
    val targetText = InspectionsBundle.message("fix.all.inspection.problems.in.file", inspection.displayName)
    val intention = myFixture.findSingleIntention(targetText)
    myFixture.launchAction(intention)
    myFixture.checkResult(after)
  }

  @Test
  fun `fix preview should work`() {
    // language=Markdown
    val before = """
    | first | table |
    |------|---|
    | some<caret> | some |
    """.trimIndent()
    myFixture.configureByText("some.md", before)
    myFixture.enableInspections(MarkdownIncorrectTableFormattingInspection())
    val fix = myFixture.getAllQuickFixes().find { it.text == reformatIntentionFixText }
    checkNotNull(fix) { "Failed to find fix" }
    timeoutRunBlocking {
      withContext(Dispatchers.EDT) {
        myFixture.checkPreviewAndLaunchAction(fix)
      }
    }
  }

  @TestFor(issues = ["IDEA-307242"])
  @Test
  fun `quick fix is available with caret after the last symbol`() {
    // language=Markdown
    val before = """
    | none | none |
    |------|------|
    | some | some   |<caret>
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
  fun `reformats to compact style`() {
    withTableStyle(myFixture.project, TableStyle.COMPACT) {
      doTest(
        """
        | a |longer|
        |---|---|
        | 1 |2|
        """.trimIndent(),
        """
        | a | longer |
        | --- | --- |
        | 1 | 2 |
        """.trimIndent()
      )
    }
  }

  @Test
  fun `reformats to tight style`() {
    withTableStyle(myFixture.project, TableStyle.TIGHT) {
      doTest(
        """
        | a | longer |
        | --- | --- |
        | 1 | 2 |
        """.trimIndent(),
        """
        |a|longer|
        |---|---|
        |1|2|
        """.trimIndent()
      )
    }
  }

  private fun doTest(content: String, after: String) {
    myFixture.configureByText("some.md", content)
    myFixture.enableInspections(MarkdownIncorrectTableFormattingInspection())
    val fix = myFixture.getAllQuickFixes().find { it.text == reformatIntentionFixText }
    assertNotNull(fix)
    myFixture.launchAction(fix!!)
    myFixture.checkResult(after)
  }

}
