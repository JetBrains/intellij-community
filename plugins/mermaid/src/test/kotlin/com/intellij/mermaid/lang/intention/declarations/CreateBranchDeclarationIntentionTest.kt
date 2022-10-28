package com.intellij.mermaid.lang.intention.declarations

import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.MermaidBaseTestCase

class CreateBranchDeclarationIntentionTest : MermaidBaseTestCase("intention/declarations/create_branch") {
  fun `test merge`() = doTest()

  fun `test checkout`() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    myFixture.configureByFile("${testName}_before.mermaid")

    val targetText = MermaidBundle.message("fix.create.branch.declaration", "main")
    val fix = myFixture.getAllQuickFixes().find { it.text == targetText }
    assertNotNull(fix)
    myFixture.checkPreviewAndLaunchAction(fix!!)
    myFixture.checkResultByFile("${testName}_after.mermaid")
  }
}
