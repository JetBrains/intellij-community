package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.TestUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PhpVscLexerTest extends LexerTestCase {

  @Override
  protected String getBundleName() {
    return TestUtil.PHP_VSC;
  }

  @Override
  protected List<String> getExtraBundleNames() {
    return Arrays.asList(TestUtil.HTML_VSC, TestUtil.CSS_VSC);
  }

  @Override
  protected String getTestDirRelativePath() {
    return "php_vsc";
  }
}
