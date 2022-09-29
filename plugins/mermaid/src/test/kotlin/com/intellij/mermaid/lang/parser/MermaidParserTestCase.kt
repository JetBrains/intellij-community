package com.intellij.mermaid.lang.parser

import com.intellij.mermaid.lang.MermaidParserDefinition
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.ParsingTestCase
import java.util.*

abstract class MermaidParserTestCase(val diagramName: String) : ParsingTestCase("parser/$diagramName", "mermaid", true, MermaidParserDefinition()) {
  override fun getTestDataPath(): String {
    return "src/test/resources"
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    return getTestName(getCamelCaseTestName(), lowercaseFirstLetter)
  }

  private fun getCamelCaseTestName(): String {
    return StringUtil.trimStart(super.getName(), "test").split(" ").toMutableList().joinToString("") { word ->
      word.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(
          Locale.getDefault()
        ) else it.toString()
      }
    }
  }
}
