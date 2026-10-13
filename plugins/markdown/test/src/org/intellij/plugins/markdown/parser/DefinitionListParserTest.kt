package org.intellij.plugins.markdown.parser

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class DefinitionListParserTest: MarkdownParsingTestCase("parser/definitionList") {
  @Test
  fun `test simple`() = parsing.doTest(true)

  @Test
  fun `test multiple definitions`() = parsing.doTest(true)

  @Test
  fun `test with paragraph before`() = parsing.doTest(true)

  @Test
  fun `test with paragraph after`() = parsing.doTest(true)

  @Test
  fun `test with inlines`() = parsing.doTest(true)

  @Test
  fun `test empty line after term`() = parsing.doTest(true)

  @Test
  fun `test two empty lines break list`() = parsing.doTest(true)

  @Test
  fun `test single empty line between definitions`() = parsing.doTest(true)

  @Test
  fun `test multiple empty lines between definitions`() = parsing.doTest(true)

  @Test
  fun `test definition continuation without indent`() = parsing.doTest(true)

  @Test
  fun `test definition continuation with indent`() = parsing.doTest(true)

  @Test
  fun `test definition with multiline continuation`() = parsing.doTest(true)

  @Test
  fun `test multiple definitions with multiline continuation`() = parsing.doTest(true)

  @BeforeEach
  fun setUp() {
    parsing.testName = parsing.testName.trimStart().replace(' ', '_')
  }
}
