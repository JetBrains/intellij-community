package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import org.junit.jupiter.api.Test

class PerlLexerTest : TextMateLexerTestCase() {
  @Test
  fun perl() = doTest("perl.pl", "perl_after.pl")

  @Test
  fun regex() = doTest("regex.pl", "regex_after.pl")

  override val testDirRelativePath = "perl"
  override val bundleName = TestUtil.PERL
}
