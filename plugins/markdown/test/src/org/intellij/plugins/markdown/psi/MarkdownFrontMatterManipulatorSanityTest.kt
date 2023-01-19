package org.intellij.plugins.markdown.psi

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.RegistryKeyRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class MarkdownFrontMatterManipulatorSanityTest: LightPlatformCodeInsightTestCase() {
  @Rule
  @JvmField
  val rule = RegistryKeyRule("markdown.experimental.frontmatter.support.enable", true)

  @Test
  fun `test replace`() {
    // language=Markdown
    val content = """
    ---
    title: Some title with link http://example.com
    tags:
      - some tag
      - some tag with link http://example.com
      - some other tag
    ---
    """.trimIndent()
    // language=Markdown
    val expected = """
    ---
    title: Some title with link https://example.com
    tags:
      - some tag
      - some tag with link https://example.com
      - some other tag
    ---
    """.trimIndent()
    configureFromFileText("some.md", content)
    val element = file.firstChild!!
    val manipulator = ElementManipulators.getNotNullManipulator(element)
    runWriteActionAndWait {
      manipulator.handleContentChange(element, TextRange(93, 100), "https://")
      commitAllDocuments()
      manipulator.handleContentChange(element, TextRange(32, 39), "https://")
      commitAllDocuments()
    }
    checkResultByText(expected)
  }
}
