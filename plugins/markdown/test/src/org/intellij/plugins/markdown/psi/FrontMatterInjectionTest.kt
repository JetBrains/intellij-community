package org.intellij.plugins.markdown.psi

import com.intellij.lang.Language
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.util.PsiUtilCore
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.typeInEditor
import org.jetbrains.yaml.YAMLLanguage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.toml.lang.TomlLanguage

@TestApplication
class FrontMatterInjectionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test yaml gets injected`() {
    val content = """
    ---
    categories:
      - Test
      - Markdown
      - Injection
    date: "22.11.2022"<caret>
    description: This is a test
    tags:
      - test
      - some
    title: Some title
    ---
    
    # Some header
    """.trimIndent()
    doTest(content, YAMLLanguage.INSTANCE)
  }

  @Test
  fun `test yaml gets injected with trailing whitespace after delimiters`() {
    doTest("---  \ntitle: Some<caret> title\n---\t\n", YAMLLanguage.INSTANCE)
  }

  @Test
  fun `test toml gets injected`() {
    val content = """
    +++
    categories = ['Test', 'Markdown', 'Injection']
    date = '22.11.2022'<caret>
    description = 'This is a test'
    tags = ['test', 'some']
    title = 'Some title'
    +++
    
    # Some header
    """.trimIndent()
    doTest(content, TomlLanguage)
  }

  /**
   * Front matter adds no prefix to a line of the injected fragment, so `EnterBetweenBracesFinalHandler` must not indent the fragment.
   * Enter keeps the default behaviour and does not move the closing brace.
   */
  @Test
  fun `test enter between braces in toml front matter`(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
      "some.md",
      """
        +++
        key = {<caret>}
        +++
      """.trimIndent()
    )

    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor('\n')
      myFixture.checkResult(
        """
          +++
          key = {
          <caret>}
          +++
        """.trimIndent()
      )
    }
  }

  private fun doTest(content: String, expectedLanguage: Language): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", content)
    readAction {
      val offset = myFixture.editor.caretModel.offset
      val element = PsiUtilCore.getElementAtOffset(myFixture.file, offset)
      val language = element.language
      assertEquals(expectedLanguage, language)
    }
  }
}
