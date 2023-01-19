package org.intellij.plugins.markdown.psi

import com.intellij.psi.util.elementType
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import junit.framework.TestCase
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader

class MarkdownHeaderContentTest: LightPlatformCodeInsightTestCase() {
  private val firstElement
    get() = file.firstChild!!

  fun `test content element can be obtained for atx header`() {
    // language=Markdown
    val content = """
    # Some atx header
    """.trimIndent()
    configureFromFileText("some.md", content)
    val header = firstElement as MarkdownHeader
    val contentElement = header.contentElement
    checkNotNull(contentElement)
    TestCase.assertEquals(MarkdownTokenTypes.ATX_CONTENT, contentElement.elementType)
    TestCase.assertTrue(contentElement.isAtxContent)
    TestCase.assertFalse(contentElement.isSetextContent)
  }

  fun `test content element can be obtained for setext header`() {
    // language=Markdown
    val content = """
    Some setext header
    ---
    """.trimIndent()
    configureFromFileText("some.md", content)
    val header = firstElement as MarkdownHeader
    val contentElement = header.contentElement
    checkNotNull(contentElement)
    TestCase.assertEquals(MarkdownTokenTypes.SETEXT_CONTENT, contentElement.elementType)
    TestCase.assertFalse(contentElement.isAtxContent)
    TestCase.assertTrue(contentElement.isSetextContent)
  }
}
