package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import org.junit.jupiter.api.Test

class PhpHtmlLexerTest : TextMateLexerTestCase() {
  @Test
  fun injection() = doTest("injection.php_hack", "injection_after.php_hack")

  override val testDirRelativePath = "php"
  override val bundleName = TestUtil.PHP
  override val extraBundleNames = listOf(TestUtil.HTML)
}
