package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.TestUtil;

public class MarkdownSublimeLexerTest extends LexerTestCase {
  @Override
  protected String getTestDirRelativePath() {
    return "markdown_sublime";
  }

  @Override
  protected String getBundleName() {
    return TestUtil.MARKDOWN_SUBLIME;
  }
}
