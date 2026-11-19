// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.injection

import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/editor/codeFence")
class MarkdownCodeFenceErrorHighlightingTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private lateinit var testName: String

  private var oldShowErrorsSetting = false

  private val settings
    get() = MarkdownSettings.getInstance()

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, true)
    oldShowErrorsSetting = settings.showProblemsInCodeBlocks
  }

  @Test
  fun testSimpleCodeFenceError() {
    settings.showProblemsInCodeBlocks = true
    myFixture.testHighlighting(true, false, false, testName + ".md")
  }

  @Test
  fun testSimpleCodeFenceNoErrors() {
    settings.showProblemsInCodeBlocks = true
    myFixture.testHighlighting(true, false, false, testName + ".md")
  }

  @Test
  fun testIndentedCodeFenceError() {
    settings.showProblemsInCodeBlocks = true
    myFixture.testHighlighting(true, false, false, testName + ".md")
  }

  @Test
  fun testIndentedCodeFenceErrorsHidden() {
    settings.showProblemsInCodeBlocks = false
    myFixture.testHighlighting(true, false, false, testName + ".md")
  }

  @Test
  fun testIndentedCodeFenceHideProblemsIntention() {
    settings.showProblemsInCodeBlocks = true
    myFixture.configureByText(
      "test.md",
      """
            ```json
            |<caret>
            ```
      """.trimIndent(),
    )

    assertNotNull(myFixture.getAvailableIntention("Hide problems in code fences"))
  }

  @AfterEach
  fun tearDown() {
    settings.showProblemsInCodeBlocks = oldShowErrorsSetting
  }
}
