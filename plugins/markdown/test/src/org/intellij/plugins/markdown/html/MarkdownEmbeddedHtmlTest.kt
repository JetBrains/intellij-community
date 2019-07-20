// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.html

import com.intellij.lang.html.HTMLLanguage
import com.intellij.psi.impl.DebugUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil

class MarkdownEmbeddedHtmlTest : BasePlatformTestCase() {
  override fun getTestDataPath(): String {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/html/embedded/"
  }

  fun testHtml1() = doTest()

  fun testHtml2() = doTest()

  fun testHtml3() = doTest()

  fun doTest() {
    val name = getTestName(true)
    val file = myFixture.configureByFile("$name.md")

    val psi = DebugUtil.psiToString(file.viewProvider.getPsi(HTMLLanguage.INSTANCE), false, false)
    val tree = myFixture.configureByFile("$name.txt").text

    TestCase.assertEquals(psi, tree)
  }
}