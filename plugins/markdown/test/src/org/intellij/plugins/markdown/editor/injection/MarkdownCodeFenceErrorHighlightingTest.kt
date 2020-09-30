// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.injection

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.settings.MarkdownApplicationSettings

class MarkdownCodeFenceErrorHighlightingTest : BasePlatformTestCase() {
  private var oldHideErrorsSetting = false

  override fun setUp() {
    super.setUp()
    oldHideErrorsSetting = MarkdownApplicationSettings.getInstance().isHideErrors
  }

  override fun getTestDataPath(): String {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/editor/codeFence/"
  }

  fun testSimpleCodeFenceError() {
    MarkdownApplicationSettings.getInstance().isHideErrors = false
    myFixture.testHighlighting(true, false, false, getTestName(true) + ".md")
  }

  fun testSimpleCodeFenceNoErrors() {
    MarkdownApplicationSettings.getInstance().isHideErrors = true
    myFixture.testHighlighting(true, false, false, getTestName(true) + ".md")
  }

  override fun tearDown() {
    try {
      MarkdownApplicationSettings.getInstance().isHideErrors = oldHideErrorsSetting
    }
    finally {
      super.tearDown()
    }
  }
}