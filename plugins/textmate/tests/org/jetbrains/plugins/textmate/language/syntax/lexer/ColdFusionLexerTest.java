package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.TestUtil;

public class ColdFusionLexerTest extends LexerTestCase {

  @Override
  protected String getTestDirRelativePath() {
    return "coldfusion";
  }

  @Override
  protected String getBundleName() {
    return TestUtil.COLD_FUSION;
  }
}
