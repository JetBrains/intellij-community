package com.intellij.mermaid.lang.parser

import com.intellij.mermaid.lang.MermaidParserDefinition
import com.intellij.mermaid.lang.MermaidTestingUtil
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.parsingFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo

abstract class MermaidParserTestCase(diagramName: String) {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val parsing by parsingFixture(
    projectFixture,
    MermaidTestingUtil.TEST_DATA_PATH + "/parser/$diagramName",
    "mermaid",
    true,
    MermaidParserDefinition(),
  )

  private lateinit var methodName: String

  @BeforeEach
  fun rememberMethodName(testInfo: TestInfo) {
    methodName = testInfo.testMethod.get().name
  }

  protected fun doTest(checkResult: Boolean) {
    val name = MermaidTestingUtil.getTestName(methodName, true)
    val text = parsing.loadFile("$name.mermaid")
    val file = parsing.parseFile(name, text)
    if (checkResult) {
      parsing.checkResult(name, file)
    }
  }
}
