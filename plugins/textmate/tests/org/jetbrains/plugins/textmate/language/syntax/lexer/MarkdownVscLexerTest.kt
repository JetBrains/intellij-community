package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import org.junit.jupiter.api.Test

class MarkdownVscLexerTest : TextMateLexerTestCase() {
  @Test
  fun heading() = doTest("heading.md", "heading_after.md")

  @Test
  fun unknownUtf() = doTest("unknown_utf.md", "unknown_utf_after.md")

  @Test
  fun inlineBold() = doTest("inline_bold.md", "inline_bold_after.md")

  override val testDirRelativePath = "markdown_vsc"
  override val bundleName = TestUtil.MARKDOWN_VSC
}
