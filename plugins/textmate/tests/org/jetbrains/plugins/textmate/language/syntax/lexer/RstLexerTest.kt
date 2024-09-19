package org.jetbrains.plugins.textmate.language.syntax.lexer

class RstLexerTest: LexerTestCase() {
  override fun getTestDirRelativePath(): String = "rst"

  override fun getBundleName(): String = "restructuredtext"
}