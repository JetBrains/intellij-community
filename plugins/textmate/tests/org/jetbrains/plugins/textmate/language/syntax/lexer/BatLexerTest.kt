package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import org.junit.jupiter.api.Test

class BatLexerTest : TextMateLexerTestCase() {
  @Test
  fun bat() = doTest("bat.bat_hack", "bat_after.bat_hack")

  override val testDirRelativePath = "bat"
  override val bundleName = TestUtil.BAT
}
