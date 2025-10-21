package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import org.junit.jupiter.api.Test

class MarkdownSublimeLexerTest : TextMateLexerTestCase() {
  @Test
  fun infinityLoopBundleBug() = doTest("infinity_loop_bundle_bug.md", "infinity_loop_bundle_bug_after.md")

  override val testDirRelativePath = "markdown_sublime"
  override val bundleName = TestUtil.MARKDOWN_SUBLIME
}
