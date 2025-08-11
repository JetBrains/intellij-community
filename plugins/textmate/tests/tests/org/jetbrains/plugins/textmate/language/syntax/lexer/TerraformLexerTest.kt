package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.TextMateLexerTestCase
import org.junit.jupiter.api.Test

class TerraformLexerTest : TextMateLexerTestCase() {
  @Test
  fun module() = doTest("module.tf", "module_after.tf")

  @Test
  fun splitScopeBySpace() = doTest("split_scope_by_space.tf", "split_scope_by_space_after.tf")

  override val testDirRelativePath = "terraform"
  override val bundleName = TestUtil.TERRAFORM
}
