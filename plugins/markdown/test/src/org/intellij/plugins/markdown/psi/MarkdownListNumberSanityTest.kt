package org.intellij.plugins.markdown.psi

import com.intellij.openapi.application.readAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownListNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownListNumberSanityTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test element is created`(): Unit = timeoutRunBlocking {
    // language=Markdown
    val content = """
    1<caret>. Some list item
    2. Some other list item
    """.trimIndent()
    myFixture.configureByText("some.md", content)
    readAction {
      val caret = myFixture.editor.caretModel.currentCaret
      val element = myFixture.file.findElementAt(caret.offset)
      checkNotNull(element)
      assertInstanceOf(MarkdownListNumber::class.java, element)
      assertEquals("1. ", element.text)
    }
  }

  @Test
  fun `test delimiter is correct`(): Unit = timeoutRunBlocking {
    // language=Markdown
    val content = """
    1<caret>. Some list item
    2. Some other list item
    """.trimIndent()
    myFixture.configureByText("some.md", content)
    readAction {
      val element = myFixture.file.findElementAt(myFixture.editor.caretModel.currentCaret.offset) as? MarkdownListNumber
      checkNotNull(element)
      assertEquals('.', element.delimiter)
    }
  }

  @Test
  fun `test number is correct`(): Unit = timeoutRunBlocking {
    // language=Markdown
    val content = """
    1<caret>. Some list item
    2. Some other list item
    """.trimIndent()
    myFixture.configureByText("some.md", content)
    readAction {
      val element = myFixture.file.findElementAt(myFixture.editor.caretModel.currentCaret.offset) as? MarkdownListNumber
      checkNotNull(element)
      assertEquals(1, element.number)
    }
  }
}
