package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.MermaidBaseTestCase

class FlowchartCompletionTest : MermaidBaseTestCase("completion/diagrams/flowchart") {
  private val directions = arrayOf("LR", "RL", "TB", "BT")
  private val extendedDirections = arrayOf("LR", "RL", "TB", "BT", "TD", "BR", "<", ">", "^", "v")

  fun `test direction at header`() = doTest(*extendedDirections)

  fun `test keyword at top level`() = doTest("subgraph")

  fun `test keyword in subgraph`() = doTest("direction", "subgraph", "name")

  fun `test direction at statement`() = doTest(*directions)

  private fun doTest(vararg variants: String) {
    val testName = getTestName(true)
    myFixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
