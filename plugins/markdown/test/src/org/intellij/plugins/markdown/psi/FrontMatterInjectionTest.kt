package org.intellij.plugins.markdown.psi

import com.intellij.lang.Language
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.RegistryKeyRule
import org.jetbrains.yaml.YAMLLanguage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.toml.lang.TomlLanguage

@RunWith(JUnit4::class)
class FrontMatterInjectionTest: LightPlatformCodeInsightTestCase() {
  @get:Rule
  val rule = RegistryKeyRule("markdown.experimental.frontmatter.support.enable", true)

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

  private fun doTest(content: String, expectedLanguage: Language) {
    configureFromFileText("some.md", content)
    val offset = editor.caretModel.offset
    val element = file.findElementAt(offset)
    checkNotNull(element) { "Failed to find injected element at $offset" }
    val language = element.language
    assertEquals(expectedLanguage, language)
  }
}
