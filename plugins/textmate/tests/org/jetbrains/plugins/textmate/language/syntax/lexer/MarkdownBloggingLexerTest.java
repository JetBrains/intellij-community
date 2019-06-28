package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.TestUtil;

import java.util.List;

import static java.util.Arrays.asList;

public class MarkdownBloggingLexerTest extends LexerTestCase {
  @Override
  protected String getTestDirRelativePath() {
    return "markdown_blogging";
  }

  @Override
  protected String getBundleName() {
    return TestUtil.MARKDOWN_BLOGGING;
  }

  @Override
  protected List<String> getExtraBundleNames() {
    return asList(TestUtil.MARKDOWN_TEXTMATE);
  }
}
