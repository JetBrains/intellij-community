package org.intellij.plugins.markdown.parser

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class CommentParsingTest: MarkdownParsingTestCase("parser/comments") {
  @Test
  fun `test single comment`() = parsing.doTest(true)

  @Test
  fun `test multiple comments`() = parsing.doTest(true)

  @BeforeEach
  fun setUp() {
    parsing.testName = parsing.testName.trimStart().replace(' ', '_')
  }
}
