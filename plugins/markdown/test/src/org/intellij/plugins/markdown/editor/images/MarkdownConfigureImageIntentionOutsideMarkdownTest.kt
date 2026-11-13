// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.intellij.plugins.markdown.images.MarkdownImagesBundle
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/editor/images/intention")
class MarkdownConfigureImageIntentionOutsideMarkdownTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private lateinit var testName: String

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, false).trimStart().replace(' ', '_')
  }

  @Test
  fun `test intention not available in html files`(): Unit = doTest()

  private fun doTest() {
    myFixture.configureByFile(getTestFileName())
    val intentions = myFixture.filterAvailableIntentions(MarkdownImagesBundle.message("markdown.configure.image.text"))
    assertThat(intentions).isEmpty()
  }

  private fun getTestFileName(): String {
    return "$testName.html"
  }
}
