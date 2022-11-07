package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.MermaidBaseTestCase

class C4CompletionTest : MermaidBaseTestCase("completion/diagrams/c4") {
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

  fun `test at first line`() = doTest("title", *simpleKeywords, *boundaryKeywords)

  fun `test right after header`() = doTest()

  fun `test inside statement`() = doTest(*simpleKeywords, *boundaryKeywords)

  private fun doTest(vararg variants: String) {
    val testName = getTestName(true)
    myFixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
