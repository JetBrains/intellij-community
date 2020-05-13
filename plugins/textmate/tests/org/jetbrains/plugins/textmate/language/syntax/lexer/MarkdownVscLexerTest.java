package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.TestUtil;

public class MarkdownVscLexerTest extends LexerTestCase {
  @Override
  protected String getTestDirRelativePath() {
    return "markdown_vsc";
  }

  @Override
  protected String getBundleName() {
    return TestUtil.MARKDOWN_VSC;
  }
}
