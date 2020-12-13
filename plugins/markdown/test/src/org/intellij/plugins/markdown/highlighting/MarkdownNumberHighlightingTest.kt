// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.highlighting

import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.codeInsight.intention.impl.config.IntentionManagerImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil

class MarkdownNumberHighlightingTest: BasePlatformTestCase() {
  override fun getTestDataPath() = MarkdownTestingUtil.TEST_DATA_PATH + "/highlighting/numbers/"

  fun testIntegralNumbers() = doTest()

  fun testRealNumbers() = doTest()

  fun testSeparators() = doTest()

  fun testNumberIsolation() = doTest()

  private fun doTest() {
    (IntentionManager.getInstance() as IntentionManagerImpl).withDisabledIntentions<Nothing> { // see IDEA-228789 and MarkdownSpellcheckerTest.testAll
      myFixture.testHighlighting(false, true, false,
                                 getTestName(true) + ".md")
    }
  }
}