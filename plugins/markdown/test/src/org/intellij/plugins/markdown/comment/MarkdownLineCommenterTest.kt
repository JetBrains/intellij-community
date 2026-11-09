// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.comment

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.PlatformTestUtil

class MarkdownLineCommenterTest: LightPlatformCodeInsightTestCase() {
  override fun getTestDataPath(): String = PluginPathManager.getPluginHomePath("markdown") + "/test/data"

  fun testSimpleLineComment() = doTest()
  fun testSimpleLineUncomment() = doTest()

  fun testCommentLineWithParenthesis() {
    enableLinkDefinitionSyntax()
    doTest()
  }
  fun testUncommentLineWithParenthesis() {
    enableLinkDefinitionSyntax()
    doTest()
  }
  fun testCommentLineWithHtmlCommentTerminator() = doTest("left --> right", "[//]: # (left --> right)")
  fun testUncommentShortHtmlComment() = doTest("<!-->", "")
  fun testUncommentShortHtmlCommentWithDash() = doTest("<!--->", "")
  fun testCommentSelectedText() = doBlockTest("before <selection>selected</selection> after", "before <!-- selected --> after")
  fun testUncommentSelectedText() = doBlockTest("before <selection><!-- selected --></selection> after", "before selected after")
  fun testBlockCommentDoesNotWrapHtmlCommentTerminator() = doBlockTest(
    "before <selection>left --> right</selection> after",
    "before left --> right after",
  )

  fun testUncommentLineWithDoubleQuotedTitle() = doTest(
    "[//]: # \"1. [IntelliJ platform overview](#intellij-platform-overview)\"",
    "1. [IntelliJ platform overview](#intellij-platform-overview)",
  )
  fun testUncommentLineWithSingleQuotedTitle() = doTest(
    "[//]: # '1. [IntelliJ platform overview](#intellij-platform-overview)'",
    "1. [IntelliJ platform overview](#intellij-platform-overview)",
  )

  fun testCommentWithoutEmptyLine() = doTest()

  fun testCommentMultipleLines() = doTest()

  // This is how this case handled in other languages
  fun `test comment sanity on empty line`() = doTest("<caret>\n", "<!--  <caret>-->\n")
  fun `test uncomment sanity on empty line`() = doTest("<!-- <caret> -->\n", "\n<caret>")

  fun testLinkDefinitionCommentEscapesParenthesesAndRemovesInsertedBlankLine() {
    enableLinkDefinitionSyntax()
    configureFromFileText("some.md", "Previous line\n<caret>Text (with link)\nNext line")

    PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_LINE)
    checkResultByText("Previous line\n\n[//]: # (Text &#40;with link&#41;)\nNext line")

    PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_LINE)
    checkResultByText("Previous line\nText (with link)\nNext line")
  }

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

  private fun doBlockTest(before: String, after: String) {
    configureFromFileText("some.md", before)
    PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_BLOCK)
    checkResultByText(after)
  }

  private fun enableLinkDefinitionSyntax() {
    Registry.get("markdown.line.comment.link.definition.syntax.enabled").setValue(true, getTestRootDisposable())
  }

  abstract class SanityTests(private val reversed: Boolean) : LightPlatformCodeInsightTestCase() {
    fun `test sanity in empty file`() = doTest("", "")

    fun `test sanity with single character`() = doTest("a", "<!-- a -->")

    fun `test sanity multiple characters`() = doTest("abc", "<!-- abc -->")

    fun `test sanity with brackets`() = doTest("(a)", "<!-- (a) -->")

    fun `test sanity with multiple brackets`() = doTest("(abc)", "<!-- (abc) -->")

    fun `test sanity with many brackets`() = doTest("(((abc)))", "<!-- (((abc))) -->")

    fun `test sanity with extra opening brackets`() = doTest("(((abc))", "<!-- (((abc)) -->")

    fun `test sanity with extra closing brackets`() = doTest("((abc)))", "<!-- ((abc))) -->")

    private fun doTest(before: String, after: String) {
      configureFromFileText("some.md", if (reversed) after else before)
      PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_LINE)
      checkResultByText(if (reversed) before else after)
    }
  }

  class CommentSanityTests: SanityTests(false)

  class UncommentSanityTests: SanityTests(true)
}
