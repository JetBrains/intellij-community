package com.intellij.mermaid.lang.intention.declarations

import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.MermaidBaseTestCase

class CreateCommitDeclarationIntentionTest : MermaidBaseTestCase("intention/declarations/create_commit") {
  fun `test cherry-pick`() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    myFixture.configureByFile("${testName}_before.mermaid")

    val targetText = MermaidBundle.message("fix.create.commit.declaration", "123")
    val fix = myFixture.getAllQuickFixes().find { it.text == targetText }
    assertNotNull(fix)
    myFixture.checkPreviewAndLaunchAction(fix!!)
    myFixture.checkResultByFile("${testName}_after.mermaid")
  }
}
