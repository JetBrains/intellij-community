// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.MarkdownTestingUtil

class MarkdownConfigureImageIntentionOutsideMarkdownTest: BasePlatformTestCase() {
  fun `test intention not available in html files`() = doTest()

  private fun doTest() {
    myFixture.configureByFile(getTestFileName())
    val intentions = myFixture.filterAvailableIntentions(MarkdownBundle.message("markdown.configure.image.text"))
    assertEmpty(intentions)
  }

  private fun getTestFileName(): String {
    return "${getTestName(false)}.html"
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  override fun getTestDataPath(): String {
    return "${MarkdownTestingUtil.TEST_DATA_PATH}/editor/images/intention/"
  }
}
