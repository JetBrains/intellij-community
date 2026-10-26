package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class GitgraphCompletionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "completion/diagrams/gitgraph")

  @Test
  fun `test at top level`(): Unit = doTest("commit", "branch", "checkout", "merge", "cherry-pick")

  @Test
  fun `test commit`(): Unit = doTest("id", "tag", "type", "msg")

  @Test
  fun `test merge`(): Unit = doTest("id", "tag", "type")

  @Test
  fun `test cherry-pick`(): Unit = doTest("id", "tag", "parent")

  @Test
  fun `test commit type`(): Unit = doTest("NORMAL", "REVERSE", "HIGHLIGHT")

  @Test
  fun `test dir`(): Unit = doTest("LR", "BT")

  private fun doTest(vararg variants: String) {
    val testName = mermaid.getTestName(true)
    mermaid.fixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
