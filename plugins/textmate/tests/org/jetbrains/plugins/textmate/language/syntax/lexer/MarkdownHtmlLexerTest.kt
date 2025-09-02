package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import org.junit.jupiter.api.Test

class MarkdownHtmlLexerTest : TextMateLexerTestCase() {
  @Test
  fun heading() = doTest("heading.md", "heading_after.md")

  override val testDirRelativePath = "markdown_html"
  override val bundleName = TestUtil.MARKDOWN_TEXTMATE
  override val extraBundleNames = listOf(TestUtil.HTML)
}
