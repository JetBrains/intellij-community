package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import org.junit.jupiter.api.Test

class ShellscriptLexerTest : TextMateLexerTestCase() {
  @Test
  fun case() = doTest("case.sh", "case_after.sh")

  @Test
  fun comment() = doTest("comment.sh", "comment_after.sh")

  @Test
  fun heredoc() = doTest("heredoc.sh", "heredoc_after.sh")

  override val testDirRelativePath = "shellscript"
  override val bundleName = TestUtil.SHELLSCRIPT
}
