package org.intellij.plugins.markdown.editor.toc

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.executeAction
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/editor/toc")
class GenerateTableOfContentsActionTest {
  private val myFixture by codeInsightModuleFixture(projectFixture)

  private lateinit var testName: String

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, true).trimStart().replace(' ', '_')
  }

  @Test
  fun `test toc idempotence`(): Unit = timeoutRunBlocking {
    val name = testName
    myFixture.configureByFile("$name.md")
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(actionId)
      myFixture.checkResultByFile("$name.after.md")
      myFixture.executeAction(actionId)
      myFixture.checkResultByFile("$name.after.md")
    }
  }

  @Test
  fun `test custom indent`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      CodeStyle.doWithTemporarySettings(myFixture.project, CodeStyle.getSettings(myFixture.project)) { settings ->
        checkNotNull(settings.getCommonSettings(MarkdownLanguage.INSTANCE).indentOptions).INDENT_SIZE = 4
        myFixture.configureByFile("toc_idempotence.md")
        myFixture.executeAction(actionId)
        myFixture.checkResultByFile("toc_idempotence.after.md")
      }
    }
  }

  @Test
  fun `test multiple toc sections update`(): Unit = doTest()

  @Test
  fun `test headers with links`(): Unit = doTest()

  @Test
  fun `test headers with images`(): Unit = doTest()

  @Test
  fun `test multiple headers with the same text`(): Unit = doTest()

  @Test
  fun `test omitted headers`(): Unit = doTest()

  @Test
  fun `test headers with comments`(): Unit = doTest()

  @Test
  fun `test headers with html`(): Unit = doTest()

  private fun doTest(): Unit = timeoutRunBlocking {
    val name = testName
    myFixture.configureByFile("$name.md")
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(actionId)
      myFixture.checkResultByFile("$name.after.md")
    }
  }

  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)

    private const val actionId = "Markdown.GenerateTableOfContents"
  }
}
