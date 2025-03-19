package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil

class GoLexerTest : LexerTestCase() {
  override fun getTestDirRelativePath(): String {
    return "go"
  }

  override fun getBundleName(): String {
    return TestUtil.GO
  }
}
