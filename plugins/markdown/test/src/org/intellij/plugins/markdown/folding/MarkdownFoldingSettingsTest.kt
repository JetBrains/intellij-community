package org.intellij.plugins.markdown.folding

import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.intellij.plugins.markdown.settings.MarkdownCodeFoldingSettings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/folding/settings")
class MarkdownFoldingSettingsTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private lateinit var testName: String

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, true).trimStart().replace(' ', '_')
  }

  @Test
  fun `test default settings`() {
    checkFolding()
  }

  @Test
  fun `test no collapse by default`() {
    MarkdownCodeFoldingSettings.getInstance().state.apply {
      collapseLinks = false
      collapseFrontMatter = false
      collapseTables = false
      collapseCodeFences = false
      collapseTableOfContents = false
      collapseDetails = false
    }
    checkFolding()
  }

  @Test
  fun `test collapse all by default`() {
    MarkdownCodeFoldingSettings.getInstance().state.apply {
      collapseLinks = true
      collapseFrontMatter = true
      collapseTables = true
      collapseCodeFences = true
      collapseTableOfContents = true
      collapseDetails = true
    }
    checkFolding()
  }

  @AfterEach
  fun tearDown() {
    MarkdownCodeFoldingSettings.getInstance().reset()
  }

  private fun checkFolding() {
    val name = testName
    myFixture.testFoldingWithCollapseStatus("${myFixture.testDataPath}/$name.md")
  }
}
