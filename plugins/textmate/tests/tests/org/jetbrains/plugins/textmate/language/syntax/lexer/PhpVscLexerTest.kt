package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import org.junit.jupiter.api.Test

class PhpVscLexerTest : TextMateLexerTestCase() {
  @Test
  fun htmlCss() = doTest("html_css.php_vsc", "html_css_after.php_vsc")

  @Test
  fun escapeSymbol() = doTest("escape_symbol.php_vsc", "escape_symbol_after.php_vsc")

  @Test
  fun slow() = doTest("slow.php_vsc", "slow_after.php_vsc")

  @Test
  fun empty() = doTest("empty.php_vsc", "empty_after.php_vsc")

  override val testDirRelativePath = "php_vsc"
  override val bundleName = TestUtil.PHP_VSC
  override val extraBundleNames = listOf(TestUtil.HTML_VSC, TestUtil.CSS_VSC)
}
