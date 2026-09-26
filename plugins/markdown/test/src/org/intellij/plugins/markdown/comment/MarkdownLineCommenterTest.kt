// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.comment

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.PluginPathManager
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.PlatformTestUtil

class MarkdownLineCommenterTest: LightPlatformCodeInsightTestCase() {
  override fun getTestDataPath(): String = PluginPathManager.getPluginHomePath("markdown") + "/test/data"

  fun testSimpleLineComment() = doTest()
  fun testSimpleLineUncomment() = doTest()

  fun testCommentLineWithParenthesis() = doTest()
  fun testUncommentLineWithParenthesis() = doTest()

  fun testCommentWithoutEmptyLine() = doTest()

  fun testCommentMultipleLines() = doTest()

  // This is how this case handled in other languages
  fun `test comment sanity on empty line`() = doTest("<caret>\n", "[//]: # (<caret>)\n")
  fun `test uncomment sanity on empty line`() = doTest("[//]: # (<caret>)\n", "\n<caret>")

  private fun doTest() {
    configureByFile("/comment/before${getTestName(false)}.md")
    PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_LINE)
    checkResultByFile("/comment/after${getTestName(false)}.md")
  }

  private fun doTest(before: String, after: String) {
    configureFromFileText("some.md", before)
    PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_LINE)
    checkResultByText(after)
  }

  abstract class SanityTests(private val reversed: Boolean) : LightPlatformCodeInsightTestCase() {
    fun `test sanity in empty file`() = doTest("", "")

    fun `test sanity with single character`() = doTest("a", "[//]: # (a)")

    fun `test sanity multiple characters`() = doTest("abc", "[//]: # (abc)")

    fun `test sanity with brackets`() = doTest("(a)", "[//]: # (&#40;a&#41;)")

    fun `test sanity with multiple brackets`() = doTest("(abc)", "[//]: # (&#40;abc&#41;)")

    fun `test sanity with many brackets`() = doTest("(((abc)))", "[//]: # (&#40;&#40;&#40;abc&#41;&#41;&#41;)")

    fun `test sanity with extra opening brackets`() = doTest("(((abc))", "[//]: # (&#40;&#40;&#40;abc&#41;&#41;)")

    fun `test sanity with extra closing brackets`() = doTest("((abc)))", "[//]: # (&#40;&#40;abc&#41;&#41;&#41;)")

    private fun doTest(before: String, after: String) {
      configureFromFileText("some.md", if (reversed) after else before)
      PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_LINE)
      checkResultByText(if (reversed) before else after)
    }
  }

  class CommentSanityTests: SanityTests(false)

  class UncommentSanityTests: SanityTests(true)
}
