// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.inspections

import com.intellij.markdown.backend.inspections.MarkdownTablePipeInCodeSpanInspection
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.intellij.plugins.markdown.MarkdownBundle
import org.junit.Test

@Suppress("MarkdownIncorrectTableFormatting")
class MarkdownTablePipeInCodeSpanInspectionTest : LightPlatformCodeInsightFixture4TestCase() {
  private val description
    get() = MarkdownBundle.message("markdown.table.pipe.in.code.span.inspection.description")

  private val fixText
    get() = MarkdownBundle.message("markdown.table.pipe.in.code.span.fix.text")

  @Test
  fun `warns on and fixes pipe inside code span in table cell`() {
    // language=Markdown
    val content = """
    | code |
    |------|
    | `a<warning descr="$description">|</warning>b` |
    """.trimIndent()
    // language=Markdown
    val after = """
    | code |
    |------|
    | `a\|b` |
    """.trimIndent()
    doTestWithFix(content, after)
  }

  @Test
  fun `warns on and fixes pipe in header cell code span`() {
    // language=Markdown
    // Two-column separator accounts for the extra column GFM creates from the pipe in the code span
    val content = """
    | `a<warning descr="$description">|</warning>b` |
    |------|------|
    | cell | cell |
    """.trimIndent()
    // language=Markdown
    val after = """
    | `a\|b` |
    |------|------|
    | cell | cell |
    """.trimIndent()
    doTestWithFix(content, after)
  }

  @Test
  fun `warns on and fixes multiple pipes in one code span`() {
    // language=Markdown
    val content = """
    | code |
    |------|
    | `a<warning descr="$description">|</warning>b<warning descr="$description">|</warning>c` |
    """.trimIndent()
    // language=Markdown
    val after = """
    | code |
    |------|
    | `a\|b\|c` |
    """.trimIndent()
    doTestWithFix(content, after)
  }

  @Test
  fun `warns on and does not re-escape already escaped pipe`() {
    // language=Markdown
    val content = """
    | code |
    |------|
    | `a\|b<warning descr="$description">|</warning>c` |
    """.trimIndent()
    // language=Markdown
    val after = """
    | code |
    |------|
    | `a\|b\|c` |
    """.trimIndent()
    doTestWithFix(content, after)
  }

  @Test
  fun `warns on and fixes pipe at start of code span content`() {
    // language=Markdown - pipe at content start (no preceding char to check)
    val content = """
    | code |
    |------|
    | `<warning descr="$description">|</warning>b` |
    """.trimIndent()
    // language=Markdown
    val after = """
    | code |
    |------|
    | `\|b` |
    """.trimIndent()
    doTestWithFix(content, after)
  }

  @Test
  fun `shows warnings for pipes in multiple code spans in one cell`() {
    // language=Markdown
    val content = """
    | code |
    |------|
    | `a<warning descr="$description">|</warning>b` and `c<warning descr="$description">|</warning>d` |
    """.trimIndent()
    doTest(content)
  }

  @Test
  fun `no warning for already escaped pipe in code span`() {
    // language=Markdown
    val content = """
    | code |
    |------|
    | `a\|b` |
    """.trimIndent()
    doTest(content)
  }

  @Test
  fun `no warning for code span outside table`() {
    // language=Markdown
    val content = "Some `a|b` text outside a table"
    doTest(content)
  }

  @Test
  fun `no warning for table cell without code span`() {
    // language=Markdown
    val content = """
    | none | none |
    |------|------|
    | some | some |
    """.trimIndent()
    doTest(content)
  }

  private fun doTest(content: String) {
    myFixture.configureByText("some.md", content)
    myFixture.enableInspections(MarkdownTablePipeInCodeSpanInspection())
    myFixture.checkHighlighting()
  }

  private fun doTestWithFix(content: String, after: String) {
    myFixture.configureByText("some.md", content)
    myFixture.enableInspections(MarkdownTablePipeInCodeSpanInspection())
    myFixture.checkHighlighting()
    val fix = myFixture.getAllQuickFixes().first { it.text == fixText }
    myFixture.launchAction(fix)
    myFixture.checkResult(after)
  }
}
