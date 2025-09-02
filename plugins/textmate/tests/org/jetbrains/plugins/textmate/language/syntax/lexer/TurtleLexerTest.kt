package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import org.junit.jupiter.api.Test

class TurtleLexerTest : TextMateLexerTestCase() {
  @Test
  fun localInifinityLoopProtection() = doTest("local_inifinity_loop_protection.ttl", "local_inifinity_loop_protection_after.ttl")

  override val testDirRelativePath = "turtle"
  override val bundleName = TestUtil.TURTLE
}
