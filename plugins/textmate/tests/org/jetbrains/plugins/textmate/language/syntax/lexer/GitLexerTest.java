package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.TestUtil;

public class GitLexerTest extends LexerTestCase {

  @Override
  protected String getTestDirRelativePath() {
    return "git";
  }

  @Override
  protected String getBundleName() {
    return TestUtil.GIT;
  }
}
