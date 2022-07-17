package org.intellij.plugins.markdown.psi

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownListNumber

class MarkdownListNumberSanityTest: LightPlatformCodeInsightTestCase() {
  fun `test element is created`() {
    // language=Markdown
    val content = """
    1<caret>. Some list item
    2. Some other list item
    """.trimIndent()
    configureFromFileText("some.md", content)
    val caret = editor.caretModel.currentCaret
    val element = file.findElementAt(caret.offset)
    checkNotNull(element)
    UsefulTestCase.assertInstanceOf(element, MarkdownListNumber::class.java)
    TestCase.assertEquals("1. ", element.text)
  }

  fun `test delimiter is correct`() {
    // language=Markdown
    val content = """
    1<caret>. Some list item
    2. Some other list item
    """.trimIndent()
    configureFromFileText("some.md", content)
    val element = file.findElementAt(editor.caretModel.currentCaret.offset) as? MarkdownListNumber
    checkNotNull(element)
    TestCase.assertEquals('.', element.delimiter)
  }

  fun `test number is correct`() {
    // language=Markdown
    val content = """
    1<caret>. Some list item
    2. Some other list item
    """.trimIndent()
    configureFromFileText("some.md", content)
    val element = file.findElementAt(editor.caretModel.currentCaret.offset) as? MarkdownListNumber
    checkNotNull(element)
    TestCase.assertEquals(1, element.number)
  }
}
