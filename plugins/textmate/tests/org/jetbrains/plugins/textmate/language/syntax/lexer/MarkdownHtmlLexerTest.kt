package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.TestUtil;

import java.util.Collections;
import java.util.List;

public class MarkdownHtmlLexerTest extends LexerTestCase {

  @Override
  protected String getBundleName() {
    return TestUtil.MARKDOWN_TEXTMATE;
  }

  @Override
  protected List<String> getExtraBundleNames() {
    return Collections.singletonList(TestUtil.HTML);
  }

  @Override
  protected String getTestDirRelativePath() {
    return "markdown_html";
  }
}
