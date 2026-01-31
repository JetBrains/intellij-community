package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import kotlin.test.Test

class RstLexerTest : TextMateLexerTestCase() {
  @Test
  fun nested() = doTest("nested.rst", "nested_after.rst")

  override val testDirRelativePath = "rst"
  override val bundleName = "restructuredtext"
}