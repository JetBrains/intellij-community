package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import org.junit.jupiter.api.Test

class MarkdownLexerTest : TextMateLexerTestCase() {
  @Test
  fun headerAfterParagraph() = doTest("header_after_paragraph.md", "header_after_paragraph_after.md")

  @Test
  fun paragraph() = doTest("paragraph.md", "paragraph_after.md")

  @Test
  fun headers() = doTest("headers.md", "headers_after.md")

  @Test
  fun newLineRequiredBug() = doTest("new_line_required_bug.md", "new_line_required_bug_after.md")

  @Test
  fun numberedList() = doTest("numbered_list.md", "numbered_list_after.md")

  @Test
  fun link() = doTest("link.md", "link_after.md")

  @Test
  fun code() = doTest("code.md", "code_after.md")

  @Test
  fun unknownUtf() = doTest("unknown_utf.md", "unknown_utf_after.md")

  @Test
  fun inlineBold() = doTest("inline_bold.md", "inline_bold_after.md")

  @Test
  fun cyrillic() = doTest("cyrillic.md", "cyrillic_after.md")

  override val testDirRelativePath = "markdown"
  override val bundleName = TestUtil.MARKDOWN_TEXTMATE
}
