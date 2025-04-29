package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.TestUtil;

public class LatexLexerTest extends LexerTestCase {

  @Override
  protected String getTestDirRelativePath() {
    return "latex";
  }

  @Override
  protected String getBundleName() {
    return TestUtil.LATEX;
  }
}
