package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.junit.jupiter.api.Test
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase

class JavaLexerTest : TextMateLexerTestCase() {
  @Test
  fun javaUnicodeLiteral() = doTest("java_unicode_literal.java", "java_unicode_literal_after.java")

  @Test
  fun java() = doTest("java.java", "java_after.java")

  override val testDirRelativePath = "java"
  override val bundleName = TestUtil.JAVA
}
