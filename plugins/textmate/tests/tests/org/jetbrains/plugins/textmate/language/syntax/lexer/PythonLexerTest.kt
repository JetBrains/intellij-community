package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import org.junit.jupiter.api.Test

class PythonLexerTest : TextMateLexerTestCase() {
  @Test
  fun selfpointer() = doTest("selfpointer.py", "selfpointer_after.py")

  @Test
  fun dictionary() = doTest("dictionary.py", "dictionary_after.py")

  override val testDirRelativePath = "python"
  override val bundleName = TestUtil.PYTHON
}
