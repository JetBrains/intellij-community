package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import org.junit.jupiter.api.Test

class ElixirLexerTest : TextMateLexerTestCase() {
  @Test
  fun simpletest() = doTest("simpleTest.ex", "simpleTest_after.ex")

  override val testDirRelativePath = TestUtil.ELIXIR
  override val bundleName = "elixir"
}
