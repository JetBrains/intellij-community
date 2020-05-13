package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.TestUtil;

public class HtmlVscLexerTest extends LexerTestCase {
  @Override
  protected String getBundleName() {
    return TestUtil.HTML_VSC;
  }

  @Override
  protected String getTestDirRelativePath() {
    return "html_vsc";
  }
}
