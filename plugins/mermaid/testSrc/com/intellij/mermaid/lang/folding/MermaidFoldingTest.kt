package com.intellij.mermaid.lang.folding

import com.intellij.mermaid.lang.MermaidBaseTestCase

class MermaidFoldingTest : MermaidBaseTestCase("folding") {
  fun `test diagram body`() = doTest()

  fun `test body in braces`() = doTest()

  fun `test subgraph`() = doTest()

  fun `test foldable element`() = doTest()

  fun `test multiline note`() = doTest()

  private fun doTest() {
    myFixture.testFolding(testDataPath + "/" + getTestName(true) + ".mermaid")
  }
}
