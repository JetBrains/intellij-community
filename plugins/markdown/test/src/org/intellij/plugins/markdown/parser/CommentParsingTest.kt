package org.intellij.plugins.markdown.parser

class CommentParsingTest: MarkdownParsingTestCase("parser/comments") {
  fun `test single comment`() = doTest(true)

  fun `test multiple comments`() = doTest(true)

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }
}
