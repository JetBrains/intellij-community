package org.intellij.plugins.markdown.psi

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.lang.Language
import com.intellij.psi.util.PsiUtilCore
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.jetbrains.yaml.YAMLLanguage
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.toml.lang.TomlLanguage

@RunWith(JUnit4::class)
class FrontMatterInjectionTest: LightPlatformCodeInsightTestCase() {
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

  @Test
  fun `test toml front matter keeps injected language formatting enabled`() {
    configureFromFileText(
      "some.md",
      """
        +++
        key = [<caret>]
        +++
      """.trimIndent()
    )

    val injectedFile = PsiUtilCore.getElementAtOffset(file, editor.caretModel.offset).containingFile
    assertNull(EnterHandlerDelegate.EP_NAME.findFirstSafe { !it.shouldFormatInjectedFragment(injectedFile) })
  }

  private fun doTest(content: String, expectedLanguage: Language) {
    configureFromFileText("some.md", content)
    val offset = editor.caretModel.offset
    val element = PsiUtilCore.getElementAtOffset(file, offset)
    val language = element.language
    assertEquals(expectedLanguage, language)
  }
}
