package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.TestUtil;

public class MakefileLexerTest extends LexerTestCase {

  @Override
  protected String getTestDirRelativePath() {
    return "make";
  }

  @Override
  protected String getBundleName() {
    return TestUtil.MAKE;
  }
}
