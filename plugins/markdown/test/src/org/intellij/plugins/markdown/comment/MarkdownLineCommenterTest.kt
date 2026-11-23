// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.comment

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data")
class MarkdownLineCommenterTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private lateinit var testName: String

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, false)
  }

  @Test
  fun testSimpleLineComment(): Unit = doTest()
  @Test
  fun testSimpleLineUncomment(): Unit = doTest()

  @Test
  @RegistryKey(key = "markdown.line.comment.link.definition.syntax.enabled", value = "true")
  fun testCommentLineWithParenthesis(): Unit = doTest()
  @Test
  @RegistryKey(key = "markdown.line.comment.link.definition.syntax.enabled", value = "true")
  fun testUncommentLineWithParenthesis(): Unit = doTest()
  @Test
  fun testCommentLineWithHtmlCommentTerminator(): Unit = doTest("left --> right", "[//]: # (left --> right)")
  @Test
  fun testUncommentShortHtmlComment(): Unit = doTest("<!-->", "")
  @Test
  fun testUncommentShortHtmlCommentWithDash(): Unit = doTest("<!--->", "")
  @Test
  fun testCommentSelectedText(): Unit = doBlockTest("before <selection>selected</selection> after", "before <!-- selected --> after")
  @Test
  fun testUncommentSelectedText(): Unit = doBlockTest("before <selection><!-- selected --></selection> after", "before selected after")
  @Test
  fun testBlockCommentDoesNotWrapHtmlCommentTerminator(): Unit = doBlockTest(
    "before <selection>left --> right</selection> after",
    "before left --> right after",
  )

  @Test
  fun testUncommentLineWithDoubleQuotedTitle(): Unit = doTest(
    "[//]: # \"1. [IntelliJ platform overview](#intellij-platform-overview)\"",
    "1. [IntelliJ platform overview](#intellij-platform-overview)",
  )
  @Test
  fun testUncommentLineWithSingleQuotedTitle(): Unit = doTest(
    "[//]: # '1. [IntelliJ platform overview](#intellij-platform-overview)'",
    "1. [IntelliJ platform overview](#intellij-platform-overview)",
  )

  @Test
  fun testCommentWithoutEmptyLine(): Unit = doTest()

  @Test
  fun testCommentMultipleLines(): Unit = doTest()

  // This is how this case handled in other languages
  @Test
  fun `test comment sanity on empty line`(): Unit = doTest("<caret>\n", "<!--  <caret>-->\n")
  @Test
  fun `test uncomment sanity on empty line`(): Unit = doTest("<!-- <caret> -->\n", "\n<caret>")

  @Test
  @RegistryKey(key = "markdown.line.comment.link.definition.syntax.enabled", value = "true")
  fun testLinkDefinitionCommentEscapesParenthesesAndRemovesInsertedBlankLine(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "Previous line\n<caret>Text (with link)\nNext line")

    withContext(Dispatchers.EDT) {
      PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_LINE)
      myFixture.checkResult("Previous line\n\n[//]: # (Text &#40;with link&#41;)\nNext line")

      PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_LINE)
      myFixture.checkResult("Previous line\nText (with link)\nNext line")
    }
  }

  private fun doTest(): Unit = timeoutRunBlocking {
    myFixture.configureByFile("/comment/before$testName.md")
    withContext(Dispatchers.EDT) {
      PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_LINE)
      myFixture.checkResultByFile("/comment/after$testName.md")
    }
  }

  private fun doTest(before: String, after: String): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", before)
    withContext(Dispatchers.EDT) {
      PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_LINE)
      myFixture.checkResult(after)
    }
  }

  private fun doBlockTest(before: String, after: String): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", before)
    withContext(Dispatchers.EDT) {
      PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_BLOCK)
      myFixture.checkResult(after)
    }
  }

  @TestApplication
  abstract class SanityTests(private val reversed: Boolean) {
    companion object {
      @JvmStatic
      protected val projectFixture = projectFixture(openAfterCreation = true)
    }

    private val myFixture by codeInsightModuleFixture(projectFixture)

    @Test
    fun `test sanity in empty file`(): Unit = doTest("", "")

    @Test
    fun `test sanity with single character`(): Unit = doTest("a", "<!-- a -->")

    @Test
    fun `test sanity multiple characters`(): Unit = doTest("abc", "<!-- abc -->")

    @Test
    fun `test sanity with brackets`(): Unit = doTest("(a)", "<!-- (a) -->")

    @Test
    fun `test sanity with multiple brackets`(): Unit = doTest("(abc)", "<!-- (abc) -->")

    @Test
    fun `test sanity with many brackets`(): Unit = doTest("(((abc)))", "<!-- (((abc))) -->")

    @Test
    fun `test sanity with extra opening brackets`(): Unit = doTest("(((abc))", "<!-- (((abc)) -->")

    @Test
    fun `test sanity with extra closing brackets`(): Unit = doTest("((abc)))", "<!-- ((abc))) -->")

    private fun doTest(before: String, after: String): Unit = timeoutRunBlocking {
      myFixture.configureByText("some.md", if (reversed) after else before)
      withContext(Dispatchers.EDT) {
        PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_LINE)
        myFixture.checkResult(if (reversed) before else after)
      }
    }
  }

  class CommentSanityTests: SanityTests(false)

  class UncommentSanityTests: SanityTests(true)
}
