package com.intellij.mermaid.lang.intention.gitgraph

import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.MermaidBaseTestCase
import com.intellij.mermaid.lang.intention.GitGraphInspection

class CreateCommitDeclarationIntentionTest : MermaidBaseTestCase("intention/gitgraph/create_commit") {
  fun `test cherry-pick`() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    myFixture.configureByFile("${testName}_before.mermaid")
    myFixture.enableInspections(GitGraphInspection())

    val targetText = MermaidBundle.message("fix.create.commit.declaration", "123")
    val fix = myFixture.getAllQuickFixes().find { it.text == targetText }
    assertNotNull(fix)
    myFixture.checkPreviewAndLaunchAction(fix!!)
    myFixture.checkResultByFile("${testName}_after.mermaid")
  }
}
