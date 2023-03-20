package org.intellij.plugins.markdown.editor.headers

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.RegistryKeyRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class MarkdownHeaderLevelInferenceTypedHandlerTest: LightPlatformCodeInsightTestCase() {
  @Rule
  @JvmField
  val rule = RegistryKeyRule("markdown.experimental.header.level.inference.enable", true)

  @Test
  fun `test simple single main header`() {
    // language=Markdown
    val content = """
    # Some header
    
    Some paragraph text
    
    <caret>
    """.trimIndent()
    // language=Markdown
    val expected = """
    # Some header
    
    Some paragraph text
    
    # <caret>
    """.trimIndent()
    doTest(content, expected)
  }

  @Test
  fun `test simple multiple headers`() {
    // language=Markdown
    val content = """
    # Some header
    
    Some paragraph text
    
    ## Some other header
    
    ### Some other header
    
    <caret>
    """.trimIndent()
    // language=Markdown
    val expected = """
    # Some header
    
    Some paragraph text
    
    ## Some other header
    
    ### Some other header
    
    ### <caret>
    """.trimIndent()
    doTest(content, expected)
  }

  @Test
  fun `test inside list with outer header`() {
    // language=Markdown
    val content = """
    ## Some header
    
    * Some list item
    * <caret>
    """.trimIndent()
    // language=Markdown
    val expected = """
    ## Some header
    
    * Some list item
    * ## <caret>
    """.trimIndent()
    doTest(content, expected)
  }

  @Test
  fun `test inside list with multiple headers`() {
    // language=Markdown
    val content = """
    ## Some header
    
    * Some list item
    * ### Some other item with header
    * <caret>
    """.trimIndent()
    // language=Markdown
    val expected = """
    ## Some header
    
    * Some list item
    * ### Some other item with header
    * ## <caret>
    """.trimIndent()
    doTest(content, expected)
  }

  @Test
  fun `test inside list item continuation with outer headers`() {
    // language=Markdown
    val content = """
    ## Some header
    
    * Some list item
    * ### Some other item with header
    * Some list item with multiple lines
      Other item line
      <caret>
    """.trimIndent()
    // language=Markdown
    val expected = """
    ## Some header
    
    * Some list item
    * ### Some other item with header
    * Some list item with multiple lines
      Other item line
      ## <caret>
    """.trimIndent()
    doTest(content, expected)
  }

  @Test
  fun `test inside list item continuation with own header inside`() {
    // language=Markdown
    val content = """
    ## Some header
    
    * Some list item
    * ### Some other item with header
    * Some list item with multiple lines
      #### Inner header inside list item
      Other item line
      <caret>
    """.trimIndent()
    // language=Markdown
    val expected = """
    ## Some header
    
    * Some list item
    * ### Some other item with header
    * Some list item with multiple lines
      #### Inner header inside list item
      Other item line
      #### <caret>
    """.trimIndent()
    doTest(content, expected)
  }

  private fun doTest(content: String, expected: String) {
    configureFromFileText("some.md", content)
    type('#')
    checkResultByText(expected)
  }
}
