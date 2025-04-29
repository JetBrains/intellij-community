package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.TestUtil;

public class ElixirLexerTest extends LexerTestCase {

  @Override
  protected String getTestDirRelativePath() {
    return "elixir";
  }

  @Override
  protected String getBundleName() {
    return TestUtil.ELIXIR;
  }
}
