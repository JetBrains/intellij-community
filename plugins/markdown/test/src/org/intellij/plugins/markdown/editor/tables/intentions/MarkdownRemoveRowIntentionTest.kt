package org.intellij.plugins.markdown.editor.tables.intentions

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.intellij.plugins.markdown.MarkdownBundle
import org.junit.Test

class MarkdownRemoveRowIntentionTest: LightPlatformCodeInsightFixture4TestCase() {
  @Test
  fun `test remove row`() {
    // language=Markdown
    val before = """
    | A  | B  | C  |
    |----|----|----|
    | 11 | 12 | 13 |
    | 21 | 22 | 23 |<caret>
    | 31 | 32 | 33 |
    """.trimIndent()
    // language=Markdown
    val after = """
    | A  | B  | C  |
    |----|----|----|
    | 11 | 12 | 13 |
    | 31 | 32 | 33 |
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `test remove row in CJK table keeps remaining rows aligned`() {
    // language=Markdown
    @Suppress("MarkdownIncorrectTableFormatting")
    val before = """
    | 名字 | 年龄 |
    |------|------|
    | 张三 | 25   |
    | 李四 | 30   |<caret>
    | 王五 | 28   |
    """.trimIndent()
    // language=Markdown
    @Suppress("MarkdownIncorrectTableFormatting")
    val after = """
    | 名字 | 年龄 |
    |------|------|
    | 张三 | 25   |
    | 王五 | 28   |
    """.trimIndent()
    doTest(before, after)
  }

  private fun doTest(content: String, after: String) {
    myFixture.configureByText("some.md", content)
    val targetText = MarkdownBundle.message("markdown.remove.row.intention.text")
    val fix = myFixture.findSingleIntention(targetText)
    myFixture.launchAction(fix)
    myFixture.checkResult(after)
  }
}