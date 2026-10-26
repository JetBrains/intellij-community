package com.intellij.mermaid.lang.intention.nbsp

import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class UnrenderableSpacesAnnotatorTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "intention/nbsp")

  @Test
  fun `test state diagram`(): Unit = doTest()

  @Test
  fun `test flowchart`(): Unit = doTest()

  private fun doTest() {
    val testName = mermaid.getTestName(true)
    mermaid.fixture.configureByFile("${testName}.mermaid")
    mermaid.fixture.checkHighlighting()
  }
}
