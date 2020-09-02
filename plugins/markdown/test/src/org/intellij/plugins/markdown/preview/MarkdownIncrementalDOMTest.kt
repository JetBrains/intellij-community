// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.preview

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.ui.preview.jcef.IncrementalDOM
import java.io.File

class MarkdownIncrementalDOMTest : BasePlatformTestCase() {
  override fun getTestDataPath(): String {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/preview/id/"
  }

  fun testJavascriptCodeFenceSimple() = doTest()

  fun testJavascriptCodeFence() = doTest()

  fun testSimpleParagraph() = doTest()

  fun testPlainTextParagraphs() = doTest()

  fun testSpecialCharactersEscape() = doTest()

  fun testHtmlAttributes() = doTest()

  fun doTest() {
    val name = getTestName(true)

    val html = File(testDataPath, "$name.html").readText().trimIndent()
    val expectedJs = File(testDataPath, "$name.js").readText().trimIndent()

    val js = IncrementalDOM.generateDomBuildCalls(html).trimIndent()
    TestCase.assertEquals(expectedJs, js)
  }
}
