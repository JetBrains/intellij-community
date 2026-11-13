package org.intellij.plugins.markdown.editor.headers

import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.typeInEditor
import org.junit.jupiter.api.Test

@TestApplication
@RegistryKey(key = "markdown.experimental.header.level.inference.enable", value = "true")
class MarkdownHeaderLevelInferenceTypedHandlerTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

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

  private fun doTest(content: String, expected: String): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor('#')
      myFixture.checkResult(expected)
    }
  }
}
