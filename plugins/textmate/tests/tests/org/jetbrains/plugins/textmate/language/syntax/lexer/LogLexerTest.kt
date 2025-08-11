package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import org.junit.jupiter.api.Test

class LogLexerTest : TextMateLexerTestCase() {
  @Test
  fun log() = doTest("log.log", "log_after.log")

  override val testDirRelativePath = "log"
  override val bundleName = TestUtil.LOG
}
