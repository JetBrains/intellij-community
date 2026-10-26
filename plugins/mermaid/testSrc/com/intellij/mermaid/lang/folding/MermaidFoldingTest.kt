package com.intellij.mermaid.lang.folding

import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class MermaidFoldingTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "folding")

  @Test
  fun `test diagram body`(): Unit = doTest()

  @Test
  fun `test body in braces`(): Unit = doTest()

  @Test
  fun `test subgraph`(): Unit = doTest()

  @Test
  fun `test foldable element`(): Unit = doTest()

  @Test
  fun `test multiline note`(): Unit = doTest()

  private fun doTest() {
    mermaid.fixture.testFolding(mermaid.testDataPath + "/" + mermaid.getTestName(true) + ".mermaid")
  }
}
