package com.intellij.mermaid.lang.parser

import com.intellij.mermaid.lang.MermaidParserDefinition
import com.intellij.mermaid.lang.MermaidTestingUtil
import com.intellij.testFramework.ParsingTestCase

abstract class MermaidParserTestCase(val diagramName: String) : ParsingTestCase("parser/$diagramName", "mermaid", true, MermaidParserDefinition()) {
  override fun getTestDataPath(): String {
    return MermaidTestingUtil.TEST_DATA_PATH
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    return MermaidTestingUtil.getTestName(name, lowercaseFirstLetter)
  }
}
