package org.intellij.plugins.markdown.psi

import com.intellij.openapi.application.readAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.util.elementType
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownHeaderContentTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private val firstElement
    get() = myFixture.file.firstChild!!

  @Test
  fun `test content element can be obtained for atx header`(): Unit = timeoutRunBlocking {
    // language=Markdown
    val content = """
    # Some atx header
    """.trimIndent()
    myFixture.configureByText("some.md", content)
    readAction {
      val header = firstElement as MarkdownHeader
      val contentElement = header.contentElement
      checkNotNull(contentElement)
      assertEquals(MarkdownTokenTypes.ATX_CONTENT, contentElement.elementType)
      assertTrue(contentElement.isAtxContent)
      assertFalse(contentElement.isSetextContent)
    }
  }

  @Test
  fun `test content element can be obtained for setext header`(): Unit = timeoutRunBlocking {
    // language=Markdown
    val content = """
    Some setext header
    ---
    """.trimIndent()
    myFixture.configureByText("some.md", content)
    readAction {
      val header = firstElement as MarkdownHeader
      val contentElement = header.contentElement
      checkNotNull(contentElement)
      assertEquals(MarkdownTokenTypes.SETEXT_CONTENT, contentElement.elementType)
      assertFalse(contentElement.isAtxContent)
      assertTrue(contentElement.isSetextContent)
    }
  }
}
