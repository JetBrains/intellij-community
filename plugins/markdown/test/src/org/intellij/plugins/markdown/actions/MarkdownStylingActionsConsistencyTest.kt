// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.executeAction
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

@Suppress("unused")
class MarkdownStylingActionsConsistencyTest {
  @TestApplication
  abstract class BaseTest(
    private val actionId: String,
    private val wrapPrefix: String,
    private val wrapSuffix: String = wrapPrefix
  ) {
    companion object {
      private val projectFixture = projectFixture(openAfterCreation = true)
    }

    private val myFixture by codeInsightModuleFixture(projectFixture)

    @Test
    fun `test single word`(): Unit = timeoutRunBlocking {
      // language=Markdown
      val content = """
      Some <selection>arbitrary</selection> text
      Some other line
      """.trimIndent()
      // language=Markdown
      val applied = """
      Some $wrapPrefix<selection>arbitrary</selection>$wrapSuffix text
      Some other line
      """.trimIndent()
      myFixture.configureByText("some.md", content)
      withContext(Dispatchers.EDT) {
        myFixture.executeAction(actionId)
        myFixture.checkResult(applied)
        myFixture.executeAction(actionId)
        myFixture.checkResult(content)
      }
    }

    @Test
    fun `test nested formatting`(): Unit = timeoutRunBlocking {
      val nestedPrefix = if (wrapPrefix == "**") "_" else "**"
      val content = "$wrapPrefix$nestedPrefix<selection>arbitrary</selection>$nestedPrefix$wrapSuffix"
      val applied = "$nestedPrefix<selection>arbitrary</selection>$nestedPrefix"
      myFixture.configureByText("some.md", content)
      withContext(Dispatchers.EDT) {
        myFixture.executeAction(actionId)
        myFixture.checkResult(applied)
      }
    }

    @Test
    fun `test disabled inside code span`(): Unit = timeoutRunBlocking {
      // language=Markdown
      val content = """
      Some `arbi<caret>trary` text
      """.trimIndent()
      myFixture.configureByText("some.md", content)
      val action = ActionManager.getInstance().getAction(actionId)
      assertFalse(withContext(Dispatchers.EDT) { EditorTestUtil.checkActionIsEnabled(myFixture.editor, action) })
      myFixture.checkResult(content)
    }

    @Test
    fun `test disabled inside fenced code block`(): Unit = timeoutRunBlocking {
      // language=Markdown
      val content = """
      Some text

      ```python
      some_cal<caret>l()
      ```
      """.trimIndent()
      myFixture.configureByText("some.md", content)
      val action = ActionManager.getInstance().getAction(actionId)
      assertFalse(withContext(Dispatchers.EDT) { EditorTestUtil.checkActionIsEnabled(myFixture.editor, action) })
      myFixture.checkResult(content)
    }

    @Test
    fun `test disabled inside inline link text`(): Unit = timeoutRunBlocking {
      val content = """
      Click [<selection>JetBrains</selection>](https://jetbrains.com) here
      """.trimIndent()
      myFixture.configureByText("some.md", content)
      val action = ActionManager.getInstance().getAction(actionId)
      assertFalse(withContext(Dispatchers.EDT) { EditorTestUtil.checkActionIsEnabled(myFixture.editor, action) })
      myFixture.checkResult(content)
    }

    @Test
    fun `test disabled inside inline link destination`(): Unit = timeoutRunBlocking {
      // language=Markdown
      val content = """
      Click [text](https://exa<caret>mple.com) here
      """.trimIndent()
      myFixture.configureByText("some.md", content)
      val action = ActionManager.getInstance().getAction(actionId)
      assertFalse(withContext(Dispatchers.EDT) { EditorTestUtil.checkActionIsEnabled(myFixture.editor, action) })
      myFixture.checkResult(content)
    }

    @Test
    fun `test disabled inside autolink`(): Unit = timeoutRunBlocking {
      // language=Markdown
      val content = """
      Visit <https://exa<caret>mple.com>
      """.trimIndent()
      myFixture.configureByText("some.md", content)
      val action = ActionManager.getInstance().getAction(actionId)
      assertFalse(withContext(Dispatchers.EDT) { EditorTestUtil.checkActionIsEnabled(myFixture.editor, action) })
      myFixture.checkResult(content)
    }

    @Test
    fun `test disabled inside test link`(): Unit = timeoutRunBlocking {
      // language=Markdown
      val content = """
      [@test] ../path/he<caret>re.py
      """.trimIndent()
      myFixture.configureByText("some.md", content)
      val action = ActionManager.getInstance().getAction(actionId)
      assertFalse(withContext(Dispatchers.EDT) { EditorTestUtil.checkActionIsEnabled(myFixture.editor, action) })
      myFixture.checkResult(content)
    }

    @Test
    fun `test disabled inside @ path`(): Unit = timeoutRunBlocking {
      // language=Markdown
      val content = """
      @path/he<caret>re.py
      """.trimIndent()
      myFixture.configureByText("some.md", content)
      val action = ActionManager.getInstance().getAction(actionId)
      assertFalse(withContext(Dispatchers.EDT) { EditorTestUtil.checkActionIsEnabled(myFixture.editor, action) })
      myFixture.checkResult(content)
    }

    @Test
    fun `test whole line`(): Unit = timeoutRunBlocking {
      // language=Markdown
      val content = """
      <selection>Some arbitrary text</selection>
      Some other line
      """.trimIndent()
      // language=Markdown
      val applied = """
      $wrapPrefix<selection>Some arbitrary text</selection>$wrapSuffix
      Some other line
      """.trimIndent()
      myFixture.configureByText("some.md", content)
      withContext(Dispatchers.EDT) {
        myFixture.executeAction(actionId)
        myFixture.checkResult(applied)
        myFixture.executeAction(actionId)
        myFixture.checkResult(content)
      }
    }

    @Test
    fun `test whole line selected by action`(): Unit = timeoutRunBlocking {
      // language=Markdown
      val content = """
      Some arbitrary<caret> text
      Some other line
      """.trimIndent()
      // language=Markdown
      val applied = """
      $wrapPrefix<selection>Some arbitrary text$wrapSuffix
      <caret></selection>Some other line
      """.trimIndent()
      val removed = """
      <selection>Some arbitrary text
      <caret></selection>Some other line
      """.trimIndent()
      myFixture.configureByText("some.md", content)
      withContext(Dispatchers.EDT) {
        myFixture.executeAction("EditorSelectLine")
        myFixture.executeAction(actionId)
        myFixture.checkResult(applied)
        myFixture.executeAction(actionId)
        myFixture.checkResult(removed)
      }
    }
  }

  class BoldActionConsistency: BaseTest(MarkdownStylingActionIds.TOGGLE_BOLD, "**")

  class ItalicActionConsistency: BaseTest(MarkdownStylingActionIds.TOGGLE_ITALIC, "*")

  class StrikethroughActionConsistency: BaseTest(
    MarkdownStylingActionIds.TOGGLE_STRIKETHROUGH,
    "~~"
  )
}
