package com.intellij.mermaid.lang.intention.unused_attributes

import com.intellij.mermaid.lang.intention.UnusedAttributesInspection
import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class UnusedAttributesInspectionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "intention/unused_attributes")

  @Test
  fun `test journey`(): Unit = doTest()

  private fun doTest() {
    val testName = mermaid.getTestName(true)
    mermaid.fixture.configureByFile("${testName}.mermaid")
    mermaid.fixture.enableInspections(UnusedAttributesInspection())
    mermaid.fixture.checkHighlighting()
  }
}
