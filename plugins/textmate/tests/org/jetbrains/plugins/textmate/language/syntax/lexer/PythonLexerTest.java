package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.TestUtil;

public class PythonLexerTest extends LexerTestCase {
  @Override
  protected String getTestDirRelativePath() {
    return "python";
  }

  @Override
  protected String getBundleName() {
    return TestUtil.PYTHON;
  }
}
