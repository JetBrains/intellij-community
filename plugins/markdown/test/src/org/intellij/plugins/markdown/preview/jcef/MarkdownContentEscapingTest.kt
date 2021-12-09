// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.preview.jcef

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.ui.scale.TestScaleHelper
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.preview.jcef.MarkdownJCEFPreviewTestUtil.collectPageSource
import org.intellij.plugins.markdown.preview.jcef.MarkdownJCEFPreviewTestUtil.createJcefTestRule
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
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
@RunWith(Parameterized::class)
class MarkdownContentEscapingTest(enableOsr: Boolean) {
  private val disposableRule = DisposableRule()

  @Before
  fun before() {
    TestScaleHelper.assumeStandalone();
    TestScaleHelper.setSystemProperty("java.awt.headless", "false");
  }

  @After
  fun after() {
    TestScaleHelper.restoreProperties();
  }

  @Rule
  @JvmField
  val ruleChain: TestRule = RuleChain.outerRule(createJcefTestRule(enableOsr)).around(disposableRule)

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
    val panel = MarkdownJCEFPreviewTestUtil.setupPreviewPanel(content, disposableRule.disposable)
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
  }

  private fun parseContentBody(html: String): Element {
    val dom = Jsoup.parse(html)
    dom.outputSettings()
      .outline(true)
      .indentAmount(0)
    return dom.body()
  }

  companion object {
    @JvmStatic
    @get:Parameterized.Parameters(name = "enableOsr = {0}")
    val modes = listOf(true, false)

    private val testPath = FileUtil.join(MarkdownTestingUtil.TEST_DATA_PATH, "preview", "jcef", "escaping")

    @ClassRule
    @JvmField
    val appRule = ApplicationRule()
  }
}
