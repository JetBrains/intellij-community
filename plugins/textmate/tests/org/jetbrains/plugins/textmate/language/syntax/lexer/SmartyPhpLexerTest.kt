package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import org.junit.jupiter.api.Test

class SmartyPhpLexerTest : TextMateLexerTestCase() {
  @Test
  fun injection() = doTest("injection.tpl_hack", "injection_after.tpl_hack")

  override val testDirRelativePath = "smarty"
  override val bundleName = TestUtil.SMARTY
  override val extraBundleNames = listOf(TestUtil.PHP)
}
