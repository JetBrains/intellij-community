package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.TestUtil;

import java.util.Collections;
import java.util.List;

public class SmartyPhpLexerTest extends LexerTestCase {

  @Override
  protected String getBundleName() {
    return TestUtil.SMARTY;
  }

  @Override
  protected List<String> getExtraBundleNames() {
    return Collections.singletonList(TestUtil.PHP);
  }

  @Override
  protected String getTestDirRelativePath() {
    return "smarty";
  }
}
