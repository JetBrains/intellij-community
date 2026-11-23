package org.intellij.plugins.markdown.psi

import com.intellij.codeInsight.intention.impl.ConvertRelativePathToAbsoluteIntentionAction
import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test

@TestApplication
class ConvertToAbsolutePathIntentionSanityTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test no exceptions`() {
    myFixture.addFileToProject("some.md", "# This is some md")
    // language=Markdown
    val content = """
    [some](./<caret>some.md)
    """.trimIndent()
    // language=Markdown
    val expected = """
    [some](/some.md)
    """.trimIndent()
    myFixture.configureByText("main.md", content)
    timeoutRunBlocking {
      withContext(Dispatchers.EDT) {
        myFixture.checkPreviewAndLaunchAction(ConvertRelativePathToAbsoluteIntentionAction())
        myFixture.checkResult(expected)
      }
    }
  }

  @Test
  fun `test image path is absolute`() {
    val image = myFixture.addFileToProject("images/image.jpg", "")
    myFixture.configureByText("main.md", "![](<caret>images/image.jpg)")
    timeoutRunBlocking {
      withContext(Dispatchers.EDT) {
        myFixture.checkPreviewAndLaunchAction(ConvertRelativePathToAbsoluteIntentionAction())
        myFixture.checkResult("![](${image.virtualFile.path})")
      }
    }
  }
}
