package org.intellij.plugins.markdown.psi

import junit.framework.TestCase
import org.intellij.markdown.MarkdownElementTypes.MARKDOWN_FILE
import org.intellij.markdown.parser.MarkdownParser
import org.intellij.plugins.markdown.lang.parser.MarkdownDefaultFlavour
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.ui.preview.html.HeaderGeneratingProvider

class HeaderAnchorGenerationConsistencyWithAstTest: MarkdownHeaderAnchorTextTest() {
  private fun generateFromPsi(content: String): String? {
    configureFromFileText("some.md", content)
    val header = firstElement as MarkdownHeader
    return header.anchorText
  }

  private fun generateFromAst(content: String): String? {
    val parser = MarkdownParser(MarkdownDefaultFlavour())
    val root = parser.parse(MARKDOWN_FILE, content, true)
    val header = root.children.first()
    return HeaderGeneratingProvider.buildAnchorText(header, content)
  }

  override fun doTest(content: String, expected: String) {
    val psi = generateFromPsi(content)
    val ast = generateFromAst(content)
    TestCase.assertNotNull(psi)
    TestCase.assertEquals(psi, ast)
  }
}
