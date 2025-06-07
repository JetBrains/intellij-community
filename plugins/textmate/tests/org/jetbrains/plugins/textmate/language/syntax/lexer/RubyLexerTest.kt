package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import org.junit.jupiter.api.Test

class RubyLexerTest : TextMateLexerTestCase() {
  @Test
  fun commentAtLineStart() = doTest("comment_at_line_start.rb", "comment_at_line_start_after.rb")

  override val testDirRelativePath = "ruby"
  override val bundleName = TestUtil.RUBY
}
