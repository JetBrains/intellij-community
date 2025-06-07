package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import org.junit.jupiter.api.Test

class ColdFusionLexerTest : TextMateLexerTestCase() {
  @Test
  fun coldfusion() = doTest("coldfusion.cfm", "coldfusion_after.cfm")

  override val testDirRelativePath = "coldfusion"
  override val bundleName = TestUtil.COLD_FUSION
}
