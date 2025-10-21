package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import org.junit.jupiter.api.Test

class DockerLexerTest : TextMateLexerTestCase() {
  @Test
  fun dockerfile() = doTest("Dockerfile", "Dockerfile_after")

  override val testDirRelativePath = "docker_vsc"
  override val bundleName = TestUtil.DOCKER
}
