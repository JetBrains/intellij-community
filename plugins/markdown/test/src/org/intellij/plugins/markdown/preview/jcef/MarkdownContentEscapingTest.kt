// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.preview.jcef

import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.io.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import java.io.File

/**
 * Run with:
 * * *intellij.test.standalone=true*
 * * *java.awt.headless=false* 
 *   it seems that JCEF initialization will check this property before the rule is applied)
 *
 * Basically, all these tests are checking if the passed to the preview HTML
 * stays the same after conversion to incremental DOM.
 */
@EnabledIfSystemProperty(named = "intellij.test.standalone", matches = "^true$")
@MarkdownPreviewTest
class MarkdownContentEscapingTest: MarkdownJcefTestCase() {

  @Test
  fun `applied patch sanity`() = doTest("appliedPatchSanity")

  @Test
  fun `backtick in code fence`() = doTest("backtickInCodeFence")

  @Test
  fun `newline in link title`() = doTest("newlineInLinkTitle")

  @Test
  fun `backtick in link title`() = doTest("backtickInLinkTitle")

  @Test
  fun `code fence with backslashes`() = doTest("codeFenceWithBackslashes")

  @Test
  fun `interesting symbols`() = doTest("interestingSymbols")

  private fun doTest(name: String) {
    val content = File(testPath, "$name.html").readText()

    val expected = parseContentBody(content)

    val got = parseContentBody(runBlocking(Dispatchers.EDT) {
      val panel = createPreview()
      panel.setupPreview()
      panel.setHtmlAndWait(content)
      panel.collectPageSource()
    })

    assertTrue(got.children().isNotEmpty())
    assertEquals(expected.html(), got.child(0).html())
  }

  private fun parseContentBody(html: String): Element {
    val dom = Jsoup.parse(html)
    dom.outputSettings()
      .outline(true)
      .indentAmount(0)
    return dom.body()
  }

  companion object {
    private val testPath = FileUtil.join(MarkdownTestingUtil.TEST_DATA_PATH, "preview", "jcef", "escaping")
  }
}
