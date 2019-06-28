package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.TestUtil;

import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;

public class PhpHtmlLexerTest extends LexerTestCase {

  @Override
  protected String getBundleName() {
    return TestUtil.PHP;
  }

  @Override
  protected List<String> getExtraBundleNames() {
    return Collections.singletonList(TestUtil.HTML);
  }

  @Override
  protected String getTestDirRelativePath() {
    return "php";
  }
}
