package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.TestUtil;

public class JavaLexerTest extends LexerTestCase {

  @Override
  protected String getTestDirRelativePath() {
    return "java";
  }

  @Override
  protected String getBundleName() {
    return TestUtil.JAVA;
  }
}
