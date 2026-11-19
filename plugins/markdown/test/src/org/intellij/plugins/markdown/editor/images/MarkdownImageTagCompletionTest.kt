// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/editor/images/completion")
class MarkdownImageTagCompletionTest {
  private val myFixture by codeInsightModuleFixture(projectFixture)

  private lateinit var testName: String

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, false).trimStart().replace(' ', '_')
  }

  @Test
  fun `test in empty file`(): Unit = doTest()

  @Test
  fun `test in paragraph`(): Unit = doTest()

  @Test
  fun `test after link`(): Unit = doTest()

  private fun doTest() {
    myFixture.configureByFile(getTestFileName())
    myFixture.type("<")
    val elements = myFixture.complete(CompletionType.BASIC)
    assertThat(elements.map { it.lookupString }).contains(completionLookupString)
    myFixture.type("i\t")
    myFixture.checkResultByFile(getTestFileNameAfter())
  }

  private fun getTestFileName(): String {
    return "$testName.md"
  }

  private fun getTestFileNameAfter(): String {
    return "$testName.after.md"
  }

  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)

    private const val completionLookupString = "img src=\"\">"
  }
}
