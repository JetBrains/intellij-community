package com.intellij.mermaid.lang.intention.nbsp

import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.MermaidBaseTestCase

class ChangeSpaceSymbolIntentionTest : MermaidBaseTestCase("intention/nbsp/change_space_symbol") {
  fun `test state diagram`() = doTest()

  fun `test flowchart`() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    myFixture.configureByFile("${testName}_before.mermaid")

    val targetText = MermaidBundle.message("fix.change.space.symbol")
    val fix = myFixture.getAllQuickFixes().find { it.text == targetText }
    assertNotNull(fix)
    myFixture.checkPreviewAndLaunchAction(fix!!)
    myFixture.checkResultByFile("${testName}_after.mermaid")
  }
}
