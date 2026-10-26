package com.intellij.mermaid.lang.intention.undeclared_generic

import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.intention.UndeclaredGenericUsageInspection
import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class RemoveGenericToDeclarationFixTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "intention/undeclared_generic/remove_generic_to_declaration")

  @Test
  fun `test not declared in class statement`(): Unit = doTest()

  @Test
  fun `test not declared in relation statement left`(): Unit = doTest()

  @Test
  fun `test not declared in relation statement right`(): Unit = doTest()

  @Test
  fun `test used in annotation`(): Unit = doTest()

  @Test
  fun `test two declarations`(): Unit = doTest()

  @Test
  fun `test generic in second declaration`(): Unit = doTest()

  private fun doTest() {
    val testName = mermaid.getTestName(true)
    mermaid.fixture.configureByFile("${testName}_before.mermaid")
    mermaid.fixture.enableInspections(UndeclaredGenericUsageInspection())

    mermaid.checkPreviewAndLaunchQuickFix(MermaidBundle.message("fix.remove.generic.to.declaration"))
    mermaid.fixture.checkResultByFile("${testName}_after.mermaid")
  }
}
