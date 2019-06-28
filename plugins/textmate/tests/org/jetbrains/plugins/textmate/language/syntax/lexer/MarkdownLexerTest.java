package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.TestUtil;

public class MarkdownLexerTest extends LexerTestCase {
  @Override
  protected String getTestDirRelativePath() {
    return "markdown";
  }

  @Override
  protected String getBundleName() {
    return TestUtil.MARKDOWN_TEXTMATE;
  }
}
