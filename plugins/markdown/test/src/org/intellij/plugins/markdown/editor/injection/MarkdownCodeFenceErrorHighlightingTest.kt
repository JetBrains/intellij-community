// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.injection

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.settings.MarkdownSettings

class MarkdownCodeFenceErrorHighlightingTest : BasePlatformTestCase() {
  private var oldShowErrorsSetting = false

  private val settings
    get() = MarkdownSettings.getInstance(project)

  override fun setUp() {
    super.setUp()
    oldShowErrorsSetting = settings.showProblemsInCodeBlocks
  }

  override fun getTestDataPath(): String {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/editor/codeFence/"
  }

  fun testSimpleCodeFenceError() {
    settings.showProblemsInCodeBlocks = true
    myFixture.testHighlighting(true, false, false, getTestName(true) + ".md")
  }

  fun testSimpleCodeFenceNoErrors() {
    settings.showProblemsInCodeBlocks = true
    myFixture.testHighlighting(true, false, false, getTestName(true) + ".md")
  }

  override fun tearDown() {
    try {
      settings.showProblemsInCodeBlocks = oldShowErrorsSetting
    }
    finally {
      super.tearDown()
    }
  }
}
