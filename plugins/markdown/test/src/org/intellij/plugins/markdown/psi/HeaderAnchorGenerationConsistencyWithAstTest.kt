package org.intellij.plugins.markdown.psi

import com.intellij.openapi.application.readAction
import com.intellij.testFramework.common.timeoutRunBlocking
import org.intellij.markdown.MarkdownElementTypes.MARKDOWN_FILE
import org.intellij.markdown.parser.MarkdownParser
import org.intellij.plugins.markdown.lang.MarkdownElementType
import org.intellij.plugins.markdown.lang.parser.MarkdownDefaultFlavour
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.ui.preview.html.HeaderAnchorCache
import org.intellij.plugins.markdown.ui.preview.html.HeaderGeneratingProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class HeaderAnchorGenerationConsistencyWithAstTest: MarkdownHeaderAnchorTextTest() {
  private fun generateFromPsi(content: String): String? = timeoutRunBlocking {
    myFixture.configureByText("some.md", content)
    readAction {
      val header = firstElement as MarkdownHeader
      header.anchorText
    }
  }

  private fun generateFromAst(content: String): String? {
    val parser = MarkdownParser(MarkdownDefaultFlavour())
    val root = parser.parse(MARKDOWN_FILE, content, true)
    val header = root.children.first()
    return HeaderGeneratingProvider.buildAnchorText(header, content)
  }

  private fun generateAllFromPsi(content: String): List<String?> = timeoutRunBlocking {
    myFixture.configureByText("some.md", content)
    readAction {
      myFixture.file.children
        .filterIsInstance<MarkdownHeader>()
        .map { it.anchorText }
    }
  }

  private fun generateAllFromAst(content: String): List<String?> {
    val parser = MarkdownParser(MarkdownDefaultFlavour())
    val root = parser.parse(MARKDOWN_FILE, content, true)
    val headerAnchorCache = HeaderAnchorCache()
    return root.children
      .filter { MarkdownElementType.isHeaderElementType(it.type) }
      .map { headerAnchorCache.buildUniqueAnchorText(it, content) }
  }

  override fun doTest(content: String, expected: String) {
    val psi = generateFromPsi(content)
    val ast = generateFromAst(content)
    assertNotNull(psi)
    assertEquals(psi, ast)
  }

  @Test
  fun `test duplicate headers keep unique anchor order consistent with psi`() {
    val content = """
      # Duplicate
      ## Duplicate
      # Duplicate
      ## Another header
      # Duplicate
    """.trimIndent()

    assertEquals(generateAllFromPsi(content), generateAllFromAst(content))
  }
}
