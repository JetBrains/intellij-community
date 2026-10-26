package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class BlockCompletionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "completion/diagrams/block")

  @Test
  fun `test at line`(): Unit = doTest("block", "columns", "space")

  @Test
  fun `test after columns`(): Unit = doTest("auto", "space")

  @Test
  fun `test after block`(): Unit = doTest("end", "space")

  @Test
  fun `test inside block`(): Unit = doTest("block", "columns", "space")

  private fun doTest(vararg variants: String) {
    val testName = mermaid.getTestName(true)
    mermaid.fixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
