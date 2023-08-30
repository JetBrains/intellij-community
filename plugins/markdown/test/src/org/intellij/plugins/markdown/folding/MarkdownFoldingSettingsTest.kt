package org.intellij.plugins.markdown.folding

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.settings.MarkdownCodeFoldingSettings

class MarkdownFoldingSettingsTest: BasePlatformTestCase() {
  fun `test default settings`() {
    checkFolding()
  }

  fun `test no collapse by default`() {
    MarkdownCodeFoldingSettings.getInstance().state.apply {
      collapseLinks = false
      collapseFrontMatter = false
      collapseTables = false
      collapseCodeFences = false
      collapseTableOfContents = false
    }
    checkFolding()
  }

  fun `test collapse all by default`() {
    MarkdownCodeFoldingSettings.getInstance().state.apply {
      collapseLinks = true
      collapseFrontMatter = true
      collapseTables = true
      collapseCodeFences = true
      collapseTableOfContents = true
    }
    checkFolding()
  }

  override fun tearDown() {
    try {
      MarkdownCodeFoldingSettings.getInstance().reset()
    } catch (exception: Exception) {
      addSuppressedException(exception)
    } finally {
      super.tearDown()
    }
  }

  private fun checkFolding() {
    val name = getTestName(true)
    myFixture.testFoldingWithCollapseStatus("$testDataPath/$name.md")
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  override fun getTestDataPath(): String {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/folding/settings"
  }
}
