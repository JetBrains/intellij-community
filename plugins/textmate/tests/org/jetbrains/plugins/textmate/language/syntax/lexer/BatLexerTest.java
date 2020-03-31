package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.TestUtil;

public class BatLexerTest extends LexerTestCase {

  @Override
  protected String getTestDirRelativePath() {
    return "bat";
  }

  @Override
  protected String getBundleName() {
    return TestUtil.BAT;
  }
}
