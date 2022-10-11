package com.intellij.mermaid.lang.folding

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MermaidFoldingTest : BasePlatformTestCase() {
  fun `test diagram body`() = doTest()

  fun `test body in braces`() = doTest()

  fun `test subgraph`() = doTest()

  fun `test foldable element`() = doTest()

  fun `test multiline note`() = doTest()

  private fun doTest() {
    myFixture.testFolding(testDataPath + "/" + getTestName(true) + ".mermaid")
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  override fun getTestDataPath(): String {
    return "src/test/resources/folding"
  }
}
