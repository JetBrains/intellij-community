package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.MermaidBaseTestCase

class GitgraphCompletionTest : MermaidBaseTestCase("completion/diagrams/gitgraph") {

  fun `test at top level`() = doTest("commit", "branch", "checkout", "merge", "cherry-pick")

  fun `test commit`() = doTest("id", "tag", "type", "msg")

  fun `test merge`() = doTest("id", "tag", "type")

  fun `test cherry-pick`() = doTest("id", "tag", "parent")

  fun `test commit type`() = doTest("NORMAL", "REVERSE", "HIGHLIGHT")

  fun `test dir`() = doTest("LR", "BT")

  private fun doTest(vararg variants: String) {
    val testName = getTestName(true)
    myFixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
