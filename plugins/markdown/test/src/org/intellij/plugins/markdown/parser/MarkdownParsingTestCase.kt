package org.intellij.plugins.markdown.parser

import com.intellij.lang.html.HTMLParserDefinition
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.parsingFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.lang.parser.MarkdownParserDefinition

abstract class MarkdownParsingTestCase(dataPath: String) {
  companion object {
    private val projectFixture = projectFixture()
  }

  protected val parsing by parsingFixture(
    projectFixture,
    MarkdownTestingUtil.TEST_DATA_PATH + "/" + dataPath,
    "md",
    true,
    MarkdownParserDefinition(),
    HTMLParserDefinition()
  )
}
