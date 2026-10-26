package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class C4CompletionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "completion/diagrams/c4")

  private val simpleKeywords = arrayOf(
    "Person_Ext",
    "Person",
    "SystemQueue_Ext",
    "SystemDb_Ext",
    "System_Ext",
    "SystemQueue",
    "SystemDb",
    "System",

    "ContainerQueue_Ext",
    "ContainerDb_Ext",
    "Container_Ext",
    "ContainerQueue",
    "ContainerDb",
    "Container",

    "ComponentQueue_Ext",
    "ComponentDb_Ext",
    "Component_Ext",
    "ComponentQueue",
    "ComponentDb",
    "Component",

    "Deployment_Node",
    "Node",
    "Node_L",
    "Node_R",

    "Rel",
    "BiRel",
    "Rel_Up",
    "Rel_U",
    "Rel_Down",
    "Rel_D",
    "Rel_Left",
    "Rel_L",
    "Rel_Right",
    "Rel_R",
    "Rel_Back",
    "RelIndex",

    "UpdateElementStyle",
    "UpdateRelStyle",
    "UpdateLayoutConfig"
  )

  private val boundaryKeywords = arrayOf(
    "Boundary",
    "Enterprise_Boundary",
    "System_Boundary",
    "Container_Boundary"
  )

  @Test
  fun `test at first line`(): Unit = doTest("title", *simpleKeywords, *boundaryKeywords)

  @Test
  fun `test right after header`(): Unit = doTest()

  @Test
  fun `test inside statement`(): Unit = doTest(*simpleKeywords, *boundaryKeywords)

  private fun doTest(vararg variants: String) {
    val testName = mermaid.getTestName(true)
    mermaid.fixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
