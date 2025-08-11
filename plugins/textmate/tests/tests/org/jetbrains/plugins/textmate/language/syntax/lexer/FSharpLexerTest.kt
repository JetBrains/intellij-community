package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import org.junit.jupiter.api.Test

class FSharpLexerTest : TextMateLexerTestCase() {
  @Test
  fun test() = doTest("test.fs", "test_after.fs")

  override val testDirRelativePath = "fsharp"
  override val bundleName = TestUtil.FSHARP
  override val extraBundleNames = listOf(TestUtil.HTML_VSC, TestUtil.MARKDOWN_VSC)
}
