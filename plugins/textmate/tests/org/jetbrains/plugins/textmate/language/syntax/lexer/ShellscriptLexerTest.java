package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.TestUtil;

public class ShellscriptLexerTest extends LexerTestCase {

  @Override
  protected String getTestDirRelativePath() {
    return "shellscript";
  }

  @Override
  protected String getBundleName() {
    return TestUtil.SHELLSCRIPT;
  }
}
