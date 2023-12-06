package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.TestUtil;

public class TerraformLexerTest extends LexerTestCase {

  @Override
  protected String getTestDirRelativePath() {
    return "terraform";
  }

  @Override
  protected String getBundleName() {
    return TestUtil.TERRAFORM;
  }
}
