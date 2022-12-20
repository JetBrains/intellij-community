package com.intellij.mermaid.lang

import com.intellij.testFramework.UsefulTestCase

object MermaidTestingUtil {
  const val TEST_DATA_PATH = "src/test/resources"

  fun getTestName(name: String?, lowercaseFirstLetter: Boolean): String {
    val testName = UsefulTestCase.getTestName(name, lowercaseFirstLetter)
    return testName.trimStart().replace(' ', '_')
  }
}
