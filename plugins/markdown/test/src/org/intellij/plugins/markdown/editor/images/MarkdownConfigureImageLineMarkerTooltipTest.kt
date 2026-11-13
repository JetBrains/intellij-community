// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.codeInsight.daemon.impl.LineMarkersPass.getDisplayedLineMarkers
import com.intellij.openapi.application.readAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.intellij.plugins.markdown.images.MarkdownImagesBundle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/editor/images/markers")
class MarkdownConfigureImageLineMarkerTooltipTest {
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
  fun `test markdown image with empty link`() {
    doTest("![some image]()", getMessage())
  }

  @Test
  fun `test markdown image with simple system path`() {
    doTest("![some image](image.png)", getMessage("image.png"))
  }

  @Test
  fun `test markdown image with relative system path`() {
    doTest("![some image](../image.png)", getMessage("image.png"))
  }

  @Test
  fun `test markdown image with long system path`() {
    doTest("![some image](/Users/user/home/Pictures/image.png)", getMessage("image.png"))
  }

  @Test
  fun `test markdown image with windows path`() {
    doTest("![some image](user\\home\\Pictures\\image.png)", getMessage("image.png"))
  }

  @Test
  fun `test markdown image with relative windows path`() {
    doTest("![some image](..\\image.png)", getMessage("image.png"))
  }

  @Test
  fun `test markdown image with url`() {
    doTest("![some image](https://some.com/sub/path/image.png)", getMessage("image.png"))
  }

  @Test
  fun `test markdown image with weird url`() {
    doTest("![some image](https://some.com/sub/actual-image?parameter=image.png)", getMessage("actual-image"))
  }

  private fun getMessage(file: String): String {
    return MarkdownImagesBundle.message("markdown.configure.image.line.marker.presentation", file)
  }

  private fun getMessage(): String {
    return MarkdownImagesBundle.message("markdown.configure.image.text")
  }

  private fun doTest(content: String, expectedTooltip: String) {
    doTest(content, listOf(expectedTooltip))
  }

  private fun doTest(content: String, expectedTooltips: Iterable<String>) {
    myFixture.configureByText(getTestFileName(), content)
    myFixture.doHighlighting()
    timeoutRunBlocking {
      readAction {
        val markers = getDisplayedLineMarkers(myFixture.editor.document, myFixture.project)
        val tooltips = markers.mapNotNull { it.lineMarkerTooltip }.sorted()
        assertEquals(expectedTooltips.sorted(), tooltips)
      }
    }
  }

  private fun getTestFileName(): String {
    return "$testName.md"
  }
}
