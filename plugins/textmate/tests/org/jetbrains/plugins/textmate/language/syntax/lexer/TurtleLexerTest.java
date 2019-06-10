package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.TestUtil;

public class TurtleLexerTest extends LexerTestCase {
  @Override
  protected String getTestDirRelativePath() {
    return "turtle";
  }

  @Override
  protected String getBundleName() {
    return TestUtil.TURTLE;
  }
}
