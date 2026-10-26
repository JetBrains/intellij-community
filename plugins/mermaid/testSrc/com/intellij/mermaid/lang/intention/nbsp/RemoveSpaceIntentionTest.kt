package com.intellij.mermaid.lang.intention.nbsp

import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class RemoveSpaceIntentionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "intention/nbsp/remove_space")

  @Test
  fun `test state diagram`(): Unit = doTest()

  @Test
  fun `test flowchart`(): Unit = doTest()

  private fun doTest() {
    val testName = mermaid.getTestName(true)
    mermaid.fixture.configureByFile("${testName}_before.mermaid")

    mermaid.checkPreviewAndLaunchQuickFix(MermaidBundle.message("fix.remove.space"))
    mermaid.fixture.checkResultByFile("${testName}_after.mermaid")
  }
}
