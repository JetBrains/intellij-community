package com.intellij.mermaid.lang.intention.declarations

import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class CreateClassDeclarationIntentionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "intention/declarations/create_class")

  @Test
  fun `test annotation statement`(): Unit = doTest()

  private fun doTest() {
    val testName = mermaid.getTestName(true)
    mermaid.fixture.configureByFile("${testName}_before.mermaid")

    mermaid.checkPreviewAndLaunchQuickFix(MermaidBundle.message("fix.create.class.declaration", "A"))
    mermaid.fixture.checkResultByFile("${testName}_after.mermaid")
  }
}
