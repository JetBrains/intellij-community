package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import kotlin.test.Test

class MakefileLexerTest : TextMateLexerTestCase() {
  @Test
  fun test() = doTest("test.mk", "test_after.mk")

  override val testDirRelativePath = "make"
  override val bundleName = TestUtil.MAKE
}
