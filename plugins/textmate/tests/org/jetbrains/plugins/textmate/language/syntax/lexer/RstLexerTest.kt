package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.junit.jupiter.api.Test
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase

class RstLexerTest : TextMateLexerTestCase() {
  @Test
  fun nested() = doTest("nested.rst", "nested_after.rst")

  override val testDirRelativePath = "rst"
  override val bundleName = "restructuredtext"
}