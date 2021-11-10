// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.MarkdownTestingUtil

class MarkdownConfigureImageLineMarkerTooltipTest: BasePlatformTestCase() {
  fun `test markdown image with empty link`() {
    doTest("![some image]()", getMessage())
  }

  fun `test markdown image with simple system path`() {
    doTest("![some image](image.png)", getMessage("image.png"))
  }

  fun `test markdown image with relative system path`() {
    doTest("![some image](../image.png)", getMessage("image.png"))
  }

  fun `test markdown image with long system path`() {
    doTest("![some image](/Users/user/home/Pictures/image.png)", getMessage("image.png"))
  }

  fun `test markdown image with windows path`() {
    doTest("![some image](user\\home\\Pictures\\image.png)", getMessage("image.png"))
  }

  fun `test markdown image with relative windows path`() {
    doTest("![some image](..\\image.png)", getMessage("image.png"))
  }

  fun `test markdown image with url`() {
    doTest("![some image](https://some.com/sub/path/image.png)", getMessage("image.png"))
  }

  fun `test markdown image with weird url`() {
    doTest("![some image](https://some.com/sub/actual-image?parameter=image.png)", getMessage("actual-image"))
  }

  private fun getMessage(file: String): String {
    return MarkdownBundle.message("markdown.configure.image.line.marker.presentation", file)
  }

  private fun getMessage(): String {
    return MarkdownBundle.message("markdown.configure.image.text")
  }

  private fun doTest(content: String, expectedTooltip: String) {
    doTest(content, listOf(expectedTooltip))
  }

  private fun doTest(content: String, expectedTooltips: Iterable<String>) {
    myFixture.configureByText(getTestFileName(), content)
    myFixture.doHighlighting()
    val markers = DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, myFixture.project)
    val tooltips = markers.mapNotNull { it.lineMarkerTooltip }.sorted()
    assertEquals(expectedTooltips.sorted(), tooltips)
  }

  private fun getTestFileName(): String {
    return "${getTestName(false)}.md"
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  override fun getTestDataPath(): String {
    return "${MarkdownTestingUtil.TEST_DATA_PATH}/editor/images/markers/"
  }
}
