// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.inspections

import com.intellij.codeInspection.InspectionsBundle
import com.intellij.idea.TestFor
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.intellij.plugins.markdown.MarkdownBundle
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * These are just sanity checks, since actual tests for reformatting are in the other files.
 */
@RunWith(JUnit4::class)
@Suppress("MarkdownIncorrectTableFormatting")
class MarkdownIncorrectTableFormattingInspectionQuickFixTest: LightPlatformCodeInsightFixture4TestCase() {
  private val reformatIntentionFixText
    get() = MarkdownBundle.message("markdown.reformat.table.intention.text")

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
    val inspection = InspectionTestUtil.instantiateTool(MarkdownIncorrectTableFormattingInspection::class.java)
    myFixture.enableInspections(inspection)
    val targetText = InspectionsBundle.message("fix.all.inspection.problems.in.file", inspection.displayName);
    val intentions = myFixture.availableIntentions
    val intention = intentions.find { it.text == targetText }
    checkNotNull(intention) { "Failed to find fix with text '$targetText'" }
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
    myFixture.checkPreviewAndLaunchAction(fix)
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

  private fun doTest(content: String, after: String) {
    myFixture.configureByText("some.md", content)
    myFixture.enableInspections(MarkdownIncorrectTableFormattingInspection())
    val fix = myFixture.getAllQuickFixes().find { it.text == reformatIntentionFixText }
    assertNotNull(fix)
    myFixture.launchAction(fix!!)
    myFixture.checkResult(after)
  }
}
