package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import org.junit.jupiter.api.Test

class GoLexerTest : TextMateLexerTestCase() {
  @Test
  fun test() = doTest("test.go", "test_after.go")

  override val testDirRelativePath = "go"
  override val bundleName = TestUtil.GO
}
