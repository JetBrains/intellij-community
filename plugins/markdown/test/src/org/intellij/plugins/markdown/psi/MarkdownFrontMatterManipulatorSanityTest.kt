package org.intellij.plugins.markdown.psi

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownFrontMatterManipulatorSanityTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

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
    myFixture.configureByText("some.md", content)
    WriteCommandAction.runWriteCommandAction(myFixture.project) {
      val element = myFixture.file.firstChild!!
      val manipulator = ElementManipulators.getNotNullManipulator(element)
      manipulator.handleContentChange(element, TextRange(93, 100), "https://")
      PsiDocumentManager.getInstance(myFixture.project).commitAllDocuments()
      manipulator.handleContentChange(element, TextRange(32, 39), "https://")
      PsiDocumentManager.getInstance(myFixture.project).commitAllDocuments()
    }
    myFixture.checkResult(expected)
  }
}
