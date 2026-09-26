package org.intellij.plugins.markdown.parser

class DefinitionListParserTest: MarkdownParsingTestCase("parser/definitionList") {
  fun `test simple`() = doTest(true)

  fun `test multiple definitions`() = doTest(true)

  fun `test with paragraph before`() = doTest(true)

  fun `test with paragraph after`() = doTest(true)

  fun `test with inlines`() = doTest(true)

  fun `test empty line after term`() = doTest(true)

  fun `test two empty lines break list`() = doTest(true)

  fun `test single empty line between definitions`() = doTest(true)

  fun `test multiple empty lines between definitions`() = doTest(true)

  fun `test definition continuation without indent`() = doTest(true)

  fun `test definition continuation with indent`() = doTest(true)

  fun `test definition with multiline continuation`() = doTest(true)

  fun `test multiple definitions with multiline continuation`() = doTest(true)

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }
}
