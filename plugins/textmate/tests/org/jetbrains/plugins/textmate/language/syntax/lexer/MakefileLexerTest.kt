package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.junit.jupiter.api.Test
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase

class MakefileLexerTest : TextMateLexerTestCase() {
  @Test
  fun test() = doTest("test.mk", "test_after.mk")

  override val testDirRelativePath = "make"
  override val bundleName = TestUtil.MAKE
}
