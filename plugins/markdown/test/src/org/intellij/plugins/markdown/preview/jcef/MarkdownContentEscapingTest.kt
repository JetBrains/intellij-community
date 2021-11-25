// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.preview.jcef

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.NonHeadlessRule
import com.intellij.ui.scale.TestScaleHelper
import com.intellij.util.ui.UIUtil
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.preview.jcef.MarkdownJCEFPreviewTestUtil.collectPageSource
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import java.io.File

/**
 * Run with *intellij.test.standalone=true*
 *
 * Basically all these tests are checking if the passed to the preview HTML
 * stays the same after conversion to incremental DOM.
 */
class MarkdownContentEscapingTest {
  @Rule
  @JvmField
  val nonHeadless: TestRule = NonHeadlessRule();
  
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
    doTest(name, false)
    doTest(name, true)
  }

  private fun doTest(name: String, osr: Boolean) {
    TestScaleHelper.assumeStandalone()
    TestScaleHelper.setRegistryProperty("ide.browser.jcef.markdownView.osr.enabled", osr.toString())

    val content = File(testPath, "$name.html").readText()
    val panel = MarkdownJCEFPreviewTestUtil.setupPreviewPanel(content)
    val expected = parseContentBody(content)
    var got = parseContentBody(panel.collectPageSource()!!)
    // can't listen for the content load, so use this primitive approach
    var counter = 5
    while (got.children().isEmpty() and (counter-- > 0)) {
      Thread.sleep(500)
      got = parseContentBody(panel.collectPageSource()!!)
    }
    assertTrue(got.children().isNotEmpty())
    assertEquals(expected.html(), got.child(0).html())
    Thread.sleep(500) // wait until com.intellij.ui.jcef.JBCefOsrHandler.onPaint is called which call invokeLater()
    UIUtil.pump()
  }

  private fun parseContentBody(html: String): Element {
    val dom = Jsoup.parse(html)
    dom.outputSettings()
      .outline(true)
      .indentAmount(0)
    return dom.body()
  }

  companion object {
    init {
      //TestScaleHelper.setRegistryProperty("ide.browser.jcef.headless.enabled", "true")
      TestScaleHelper.setRegistryProperty("ide.browser.jcef.testMode.enabled", "true")
    }

    private val testPath = FileUtil.join(MarkdownTestingUtil.TEST_DATA_PATH!!, "preview", "jcef", "escaping")

    @ClassRule
    @JvmField
    val appRule = ApplicationRule()

    @AfterClass
    @JvmStatic
    fun after() {
      TestScaleHelper.restoreRegistryProperties()
    }
  }
}
