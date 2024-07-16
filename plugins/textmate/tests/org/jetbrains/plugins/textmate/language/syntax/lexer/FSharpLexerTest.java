package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.TestUtil;

import java.util.Arrays;
import java.util.List;

public class FSharpLexerTest extends LexerTestCase {
  @Override
  protected String getTestDirRelativePath() {
    return "fsharp";
  }

  @Override
  protected String getBundleName() {
    return  TestUtil.FSHARP;
  }

  @Override
  protected List<String> getExtraBundleNames() {
    return Arrays.asList(TestUtil.HTML_VSC, TestUtil.MARKDOWN_VSC);
  }
}
