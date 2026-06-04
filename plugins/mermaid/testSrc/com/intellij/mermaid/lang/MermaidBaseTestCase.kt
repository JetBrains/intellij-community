package com.intellij.mermaid.lang

import com.intellij.testFramework.fixtures.BasePlatformTestCase

abstract class MermaidBaseTestCase(private val testName: String) : BasePlatformTestCase() {
  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    return MermaidTestingUtil.getTestName(name, lowercaseFirstLetter)
  }

  override fun getTestDataPath(): String {
    return "${MermaidTestingUtil.TEST_DATA_PATH}/$testName"
  }
}
