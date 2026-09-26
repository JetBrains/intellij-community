package com.intellij.mermaid.lang.intention.gitgraph

import com.intellij.mermaid.lang.MermaidBaseTestCase
import com.intellij.mermaid.lang.intention.GitGraphInspection

class GitGraphInspectionTest : MermaidBaseTestCase("intention/gitgraph") {
  fun `test merge`() = doTest()

  fun `test merge no commits`() = doTest()

  fun `test merge itself`() = doTest()

  fun `test merge using existing id`() = doTest()

  fun `test merge same heads`() = doTest()

  fun `test checkout`() = doTest()

  fun `test cherry-pick`() = doTest()

  fun `test commit`() = doTest()

  fun `test branch`() = doTest()

  fun `test no conflicts`() = doTest()

  fun `test cherry-pick of merge commit without parent`() = doTest()

  fun `test cherry-pick of merge commit with wrong parent`() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    myFixture.configureByFile("${testName}.mermaid")
    myFixture.enableInspections(GitGraphInspection())
    myFixture.checkHighlighting()
  }
}
