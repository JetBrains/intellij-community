package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import kotlin.test.Test

class YamlLexerTest : TextMateLexerTestCase() {
  @Test
  fun test() = doTest("pnpm-lock.yaml", "pnpm-lock.txt")

  override val testDirRelativePath = "yaml"
  override val bundleName = "yaml"
}
