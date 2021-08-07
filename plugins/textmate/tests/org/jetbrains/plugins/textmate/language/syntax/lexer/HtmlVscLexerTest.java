package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.TestUtil;

import java.util.Collections;
import java.util.List;

public class HtmlVscLexerTest extends LexerTestCase {
  @Override
  protected String getBundleName() {
    return TestUtil.HTML_VSC;
  }

  @Override
  protected List<String> getExtraBundleNames() {
    return Collections.singletonList(TestUtil.CSS_VSC);
  }

  @Override
  protected String getTestDirRelativePath() {
    return "html_vsc";
  }
}
