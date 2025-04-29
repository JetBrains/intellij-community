package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import org.junit.jupiter.api.Test

class HtmlVscLexerTest : TextMateLexerTestCase() {
  @Test
  fun doctype() = doTest("doctype.html", "doctype_after.html")

  @Test
  fun htmlCss() = doTest("html_css.html", "html_css_after.html")

  override val testDirRelativePath = "html_vsc"
  override val bundleName = TestUtil.HTML_VSC
  override val extraBundleNames = listOf(TestUtil.CSS_VSC)
}
