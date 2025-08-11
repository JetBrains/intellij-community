package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import org.junit.jupiter.api.Test

class AstroLexerTest : TextMateLexerTestCase() {
  @Test
  fun text() = doTest("text.astro", "text_after.astro")

  override val testDirRelativePath = "astro"
  override val bundleName = "astro"

  override val extraBundleNames = listOf(TestUtil.JAVASCRIPT, TestUtil.CSS)
}