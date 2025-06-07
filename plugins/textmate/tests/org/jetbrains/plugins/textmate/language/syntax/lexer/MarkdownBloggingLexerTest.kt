package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import org.junit.jupiter.api.Test

class MarkdownBloggingLexerTest : TextMateLexerTestCase() {
  @Test
  fun ruby12703() = doTest("ruby12703.blog.md", "ruby12703_after.blog.md")

  override val testDirRelativePath = "markdown_blogging"
  override val bundleName = TestUtil.MARKDOWN_BLOGGING
  override val extraBundleNames = listOf(TestUtil.MARKDOWN_TEXTMATE)
}
