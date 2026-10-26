package com.intellij.mermaid.lang.intention.undeclared_generic

import com.intellij.mermaid.lang.intention.UndeclaredGenericUsageInspection
import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class UndeclaredGenericUsageInspectionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "intention/undeclared_generic")

  @Test
  fun `test not declared in class statement`(): Unit = doTest()

  @Test
  fun `test no warning`(): Unit = doTest()

  @Test
  fun `test generic in second declaration`(): Unit = doTest()

  private fun doTest() {
    val testName = mermaid.getTestName(true)
    mermaid.fixture.configureByFile("${testName}.mermaid")
    mermaid.fixture.enableInspections(UndeclaredGenericUsageInspection())
    mermaid.fixture.checkHighlighting()
  }
}
