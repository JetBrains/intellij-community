// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.comment

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.PluginPathManager
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.PlatformTestUtil

class MarkdownCommenterTest: LightPlatformCodeInsightTestCase() {
  override fun getTestDataPath(): String = PluginPathManager.getPluginHomePath("markdown") + "/test/data"

  fun testSimpleLineComment() { doTest() }
  fun testSimpleLineUncomment() { doTest() }

  fun testCommentLineWithParenthesis() { doTest() }
  fun testUncommentLineWithParenthesis() { doTest() }

  fun testCommentWithoutEmptyLine() { doTest() }

  private fun doTest() {
    configureByFile("/comment/before" + getTestName(false) + ".md")
    PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_LINE)
    checkResultByFile("/comment/after" + getTestName(false) + ".md")
  }
}