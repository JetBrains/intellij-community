package com.intellij.mermaid.lang.intention.gitgraph

import com.intellij.mermaid.lang.intention.GitGraphInspection
import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class GitGraphInspectionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "intention/gitgraph")

  @Test
  fun `test merge`(): Unit = doTest()

  @Test
  fun `test merge no commits`(): Unit = doTest()

  @Test
  fun `test merge itself`(): Unit = doTest()

  @Test
  fun `test merge using existing id`(): Unit = doTest()

  @Test
  fun `test merge same heads`(): Unit = doTest()

  @Test
  fun `test checkout`(): Unit = doTest()

  @Test
  fun `test cherry-pick`(): Unit = doTest()

  @Test
  fun `test commit`(): Unit = doTest()

  @Test
  fun `test branch`(): Unit = doTest()

  @Test
  fun `test no conflicts`(): Unit = doTest()

  @Test
  fun `test cherry-pick of merge commit without parent`(): Unit = doTest()

  @Test
  fun `test cherry-pick of merge commit with wrong parent`(): Unit = doTest()

  private fun doTest() {
    val testName = mermaid.getTestName(true)
    mermaid.fixture.configureByFile("${testName}.mermaid")
    mermaid.fixture.enableInspections(GitGraphInspection())
    mermaid.fixture.checkHighlighting()
  }
}
