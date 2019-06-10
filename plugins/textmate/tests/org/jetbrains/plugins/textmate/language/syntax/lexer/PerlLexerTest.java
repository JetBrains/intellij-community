package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.TestUtil;

public class PerlLexerTest extends LexerTestCase {

  @Override
  protected String getTestDirRelativePath() {
    return "perl";
  }

  @Override
  protected String getBundleName() {
    return TestUtil.PERL;
  }
}
