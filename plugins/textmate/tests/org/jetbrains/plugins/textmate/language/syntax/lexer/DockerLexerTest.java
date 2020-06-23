package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.plugins.textmate.TestUtil;

public class DockerLexerTest extends LexerTestCase {
  @Override
  protected String getBundleName() {
    return TestUtil.DOCKER;
  }

  @Override
  protected String getTestDirRelativePath() {
    return "docker_vsc";
  }
}
