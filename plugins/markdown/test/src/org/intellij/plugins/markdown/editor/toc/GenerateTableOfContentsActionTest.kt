package org.intellij.plugins.markdown.editor.toc

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil

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

  fun `test headers with links`() = doTest()

  fun `test headers with images`() = doTest()

  fun `test multiple headers with the same text`() = doTest()

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
