package com.intellij.grazie.ide.language.python

import com.intellij.grazie.GrazieTestBase

class PythonGrazieSupportTest : GrazieTestBase() {
  override fun getBasePath() = "python/python-grazie/test/testData"
  override fun isCommunity() = true

  fun `test grammar check in constructs`() {
    runHighlightTestForFile("Constructs.py")
  }

  fun `test grammar check in docs`() {
    runHighlightTestForFile("Docs.py")
  }

  fun `test grammar check in string literals`() {
    runHighlightTestForFile("StringLiterals.py")
  }
}
