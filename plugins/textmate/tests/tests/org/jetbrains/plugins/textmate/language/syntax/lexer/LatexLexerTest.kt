package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import kotlin.test.Test

class LatexLexerTest : TextMateLexerTestCase() {
  @Test
  fun text() = doTest("text.tex", "text_after.tex")

  override val testDirRelativePath = "latex"
  override val bundleName = TestUtil.LATEX
}
