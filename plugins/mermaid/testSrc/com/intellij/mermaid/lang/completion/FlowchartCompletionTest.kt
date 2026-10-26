package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class FlowchartCompletionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "completion/diagrams/flowchart")

  private val directions = arrayOf("LR", "RL", "TB", "BT")
  private val extendedDirections = arrayOf("LR", "RL", "TB", "BT", "TD", "BR", "<", ">", "^", "v")

  @Test
  fun `test direction at header`(): Unit = doTest(*extendedDirections)

  @Test
  fun `test keyword at top level`(): Unit = doTest("subgraph")

  @Test
  fun `test keyword in subgraph`(): Unit = doTest("direction", "subgraph", "name")

  @Test
  fun `test direction at statement`(): Unit = doTest(*directions)

  private fun doTest(vararg variants: String) {
    val testName = mermaid.getTestName(true)
    mermaid.fixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
