// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.highlighting

import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil

class MarkdownEmbeddedHtmlHighlightingTest : BasePlatformTestCase() {

  override fun getTestDataPath(): String = MarkdownTestingUtil.TEST_DATA_PATH + "/highlighting"

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

    EditorTestUtil.testFileSyntaxHighlighting(file, "$testDataPath/${getTestName(true)}.txt", true)
  }
}
