package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import kotlin.test.Test

class CppLexerTest : TextMateLexerTestCase() {
  @Test
  fun test() = doTest("test.cc", "test_after.cc")

  @Test
  fun typePrimitiveCapture() = doTest("typePrimitiveCapture.cpp", "typePrimitiveCapture_after.cpp")

  @Test
  fun numericCapture() = doTest("numericCapture.cpp", "numericCapture_after.cpp")

  override val testDirRelativePath = "cpp"
  override val bundleName = "cpp"
}
