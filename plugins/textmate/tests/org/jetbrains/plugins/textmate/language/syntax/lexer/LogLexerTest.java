package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.TestUtil;

public class LogLexerTest extends LexerTestCase {

  @Override
  protected String getTestDirRelativePath() {
    return "log";
  }

  @Override
  protected String getBundleName() {
    return TestUtil.LOG;
  }
}
