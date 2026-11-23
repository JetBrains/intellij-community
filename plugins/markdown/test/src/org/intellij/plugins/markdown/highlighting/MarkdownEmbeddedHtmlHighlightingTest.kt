// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.highlighting

import com.intellij.openapi.application.readAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/highlighting")
class MarkdownEmbeddedHtmlHighlightingTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private lateinit var testName: String

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, true)
  }

  @Test
  fun testHtmlBlockContentIsHighlightedAsHtml() {
    val text = """
        Some text.

        <section class="hero">
          <div id="main">Hello</div>
        </section>
        
        <!--comment-->
      """.trimIndent()
    val file = myFixture.configureByText("test.md", text)
    myFixture.checkHighlighting()

    timeoutRunBlocking { readAction { EditorTestUtil.testFileSyntaxHighlighting(file, "${myFixture.testDataPath}/${testName}.txt", true) } }
  }

  @Test
  fun testInlineHtmlContentIsHighlightedAsHtml() {
    val file = myFixture.configureByText("test.md", "Some text with embedded <b>html</b>.")
    timeoutRunBlocking { readAction { EditorTestUtil.testFileSyntaxHighlighting(file, "${myFixture.testDataPath}/${testName}.txt", true) } }
  }

  @Test
  fun testMultilineHtmlComment() {
    val text = """
        Some text.

        <!--
        multiline
        comment
        -->

        Trailing text.
      """.trimIndent()
    val file = myFixture.configureByText("test.md", text)
    timeoutRunBlocking { readAction { EditorTestUtil.testFileSyntaxHighlighting(file, "${myFixture.testDataPath}/${testName}.txt", true) } }
  }
}
