package org.intellij.plugins.markdown.editor.toc

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.junit.Ignore

class GenerateTableOfContentsActionTest: LightPlatformCodeInsightTestCase() {
  fun `test toc idempotence`() {
    val name = getTestName(true)
    configureByFile("$name.md")
    executeAction(actionId)
    checkResultByFile("$name.after.md")
    executeAction(actionId)
    checkResultByFile("$name.after.md")
  }

  fun `test multiple toc sections update`() = doTest()

  @Ignore
  fun `test headers with links`() = doTest()

  private fun doTest() {
    val name = getTestName(true)
    configureByFile("$name.md")
    executeAction(actionId)
    checkResultByFile("$name.after.md")
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  override fun getTestDataPath(): String {
    return "${MarkdownTestingUtil.TEST_DATA_PATH}/editor/toc/"
  }

  companion object {
    private const val actionId = "Markdown.GenerateTableOfContents"
  }
}
