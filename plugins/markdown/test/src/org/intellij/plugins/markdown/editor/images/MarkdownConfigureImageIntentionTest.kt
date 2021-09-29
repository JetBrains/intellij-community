// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.MarkdownTestingUtil

class MarkdownConfigureImageIntentionTest: BasePlatformTestCase() {
  fun `test markdown image`() = doTest()

  fun `test markdown image inside text html`() = doTest()

  fun `test markdown image inside reference`() = doTest()

  fun `test reference inside markdown image`() = doTest()

  fun `test no markdown image inside html block1`() = doTest(shouldHaveIntentions = false)

  fun `test no markdown image inside html block2`() = doTest(shouldHaveIntentions = false)

  fun `test no markdown image inside html block3`() = doTest(shouldHaveIntentions = false)

  fun `test html block`() = doTest()

  fun `test no around html image1`() = doTest(shouldHaveIntentions = false)

  fun `test no around html image2`() = doTest(shouldHaveIntentions = false)

  fun `test no around html image3`() = doTest(shouldHaveIntentions = false)

  fun `test text html`() = doTest()

  private fun doTest(shouldHaveIntentions: Boolean = true) {
    myFixture.configureByFile(getTestFileName())
    // Workaround for org.intellij.plugins.markdown.injection.MarkdownCodeFenceErrorHighlightingIntention.SettingsListener
    (myFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
    val intentions = myFixture.filterAvailableIntentions(MarkdownBundle.message("markdown.configure.image.intention.name"))
    when {
      shouldHaveIntentions -> assertSize(1, intentions)
      else -> assertEmpty(intentions)
    }
  }

  private fun getTestFileName(): String {
    return "${getTestName(false)}.md"
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  override fun getTestDataPath(): String {
    return "${MarkdownTestingUtil.TEST_DATA_PATH}/editor/images/intention/"
  }
}
