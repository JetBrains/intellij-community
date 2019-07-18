package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.TestUtil;

public class RubyLexerTest extends LexerTestCase {
  @Override
  protected String getTestDirRelativePath() {
    return "ruby";
  }

  @Override
  protected String getBundleName() {
    return TestUtil.RUBY;
  }
}
