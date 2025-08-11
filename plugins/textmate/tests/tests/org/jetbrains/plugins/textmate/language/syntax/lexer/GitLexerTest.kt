package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.junit.jupiter.api.Test
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase

class GitLexerTest : TextMateLexerTestCase() {
  @Test
  fun commitEDITMSG() = doTest("COMMIT_EDITMSG", "COMMIT_EDITMSG_after")

  override val testDirRelativePath = "git"
  override val bundleName = TestUtil.GIT
}
