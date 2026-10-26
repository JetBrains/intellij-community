package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class StateCompletionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "completion/diagrams/state")

  private val keywords = arrayOf("state", "direction", "note")
  private val directions = arrayOf("LR", "RL", "TB", "BT")

  @Test
  fun `test at top level`(): Unit = doTest(*keywords)

  @Test
  fun `test inside state`(): Unit = doTest(*keywords, "name")

  @Test
  fun `test after description`(): Unit = doTest("as")

  @Test
  fun `test after note`(): Unit = doTest("right of", "left of")

  @Test
  fun `test directions`(): Unit = doTest(*directions)

  private fun doTest(vararg variants: String) {
    val testName = mermaid.getTestName(true)
    mermaid.fixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
