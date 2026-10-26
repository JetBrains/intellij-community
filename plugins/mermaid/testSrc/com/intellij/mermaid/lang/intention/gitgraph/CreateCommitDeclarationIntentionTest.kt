package com.intellij.mermaid.lang.intention.gitgraph

import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.intention.GitGraphInspection
import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class CreateCommitDeclarationIntentionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "intention/gitgraph/create_commit")

  @Test
  fun `test cherry-pick`(): Unit = doTest()

  private fun doTest() {
    val testName = mermaid.getTestName(true)
    mermaid.fixture.configureByFile("${testName}_before.mermaid")
    mermaid.fixture.enableInspections(GitGraphInspection())

    mermaid.checkPreviewAndLaunchQuickFix(MermaidBundle.message("fix.create.commit.declaration", "123"))
    mermaid.fixture.checkResultByFile("${testName}_after.mermaid")
  }
}
