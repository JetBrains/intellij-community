// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.html

import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.application.readAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.impl.DebugUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/html/embedded")
class MarkdownEmbeddedHtmlTest {
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
  fun testHtml1(): Unit = doTest()

  @Test
  fun testHtml2(): Unit = doTest()

  @Test
  fun testHtml3(): Unit = doTest()

  @Test
  fun testTrailingComment(): Unit = doTest()

  fun doTest() {
    val name = testName
    val file = myFixture.configureByFile("$name.md")

    val psi = timeoutRunBlocking { readAction { DebugUtil.psiToString(file.viewProvider.getPsi(HTMLLanguage.INSTANCE)!!, true, false) } }
    val treeFile = myFixture.configureByFile("$name.txt")
    val tree = timeoutRunBlocking { readAction { treeFile.text } }

    assertEquals(psi, tree)
  }
}