// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.markdown.frontend.actions.InsertEmptyTableAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.UiInterceptors
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.executeAction
import org.intellij.plugins.markdown.editor.typeInEditor
import org.intellij.plugins.markdown.lang.formatter.settings.TableStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.event.ActionEvent

@TestApplication
@Suppress("MarkdownIncorrectTableFormatting", "MarkdownNoTableBorders")
class MarkdownTableEnterTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun `test single enter inside cell`() {
    // language=Markdown
    val before = """
    | none | none |
    |------|------|
    | some<caret> | some |
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none |
    |------|------|
    | some<br/><caret> | some |
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `test double enter inside cell`() {
    // language=Markdown
    val before = """
    | none | none |
    |------|------|
    | some<caret> | some |
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none |
    |------|------|
    | some
    <caret>| some |
    """.trimIndent()
    doTest(before, after, count = 2)
  }

  @Test
  fun `test single enter at the row end`() {
    // language=Markdown
    val before = """
    | none | none |
    |------|------|
    | some | some |<caret>
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none |
    |------|------|
    | some | some |
    
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `test shift enter at the row end`() {
    // language=Markdown
    val before = """
    | none | none |
    |------|------|
    | some | some |<caret>
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none |
    |------|------|
    | some | some |
    | <caret>     |      |
    """.trimIndent()
    doTest(before, after, shift = true)
  }

  @Test
  fun `test shift enter at the row end inside list item`() {
    // language=Markdown
    val before = "- item  \n\n  | none | none |\n  |------|------|\n  | some | some |<caret>"
    // language=Markdown
    val after = "- item  \n\n  | none | none |\n  |------|------|\n  | some | some |\n  | <caret>     |      |"
    doTest(before, after, shift = true)
  }

  @Test
  fun `test shift enter at the row end in compact table`() {
    withTableStyle(myFixture.project, TableStyle.COMPACT) {
      val before = """
      | a | b |
      | --- | --- |
      | some | some |<caret>
      """.trimIndent()
      val after = """
      | a | b |
      | --- | --- |
      | some | some |
      | <caret>| |
      """.trimIndent()
      doTest(before, after, shift = true)
    }
  }

  @Test
  fun `test shift enter at the row end in tight table`() {
    withTableStyle(myFixture.project, TableStyle.TIGHT) {
      val before = """
      |a|b|
      |---|---|
      |some|some|<caret>
      """.trimIndent()
      val after = """
      |a|b|
      |---|---|
      |some|some|
      |<caret>||
      """.trimIndent()
      doTest(before, after, shift = true)
    }
  }

  @Test
  fun `test shift enter at the row end with CJK header`() {
    setupTableCellsAlignedVisually(false, disposable)
    // language=Markdown
    val before = """
    | 名字 | 年龄 |
    |------|------|
    | 张三 | 25   |<caret>
    """.trimIndent()
    // language=Markdown
    val after = """
    | 名字 | 年龄 |
    |------|------|
    | 张三 | 25   |
    | <caret>     |      |
    """.trimIndent()
    doTest(before, after, shift = true)
  }

  @Test
  fun `test shift enter at the separator row end`() {
    // language=Markdown
    val before = """
    | none | none |
    |------|------|<caret>
    | some | some |
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none |
    |------|------|
    | <caret>     |      |
    | some | some |
    """.trimIndent()
    doTest(before, after, shift = true)
  }

  @Test
  fun `test shift enter at the header row end`() {
    // language=Markdown
    val before = """
    | none | none |<caret>
    |------|------|
    | some | some |
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none |
    <caret>
    |------|------|
    | some | some |
    """.trimIndent()
    doTest(before, after, shift = true)
  }

  @Test
  fun `test insert table inside blockquote`(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "> <caret>")

    withContext(Dispatchers.EDT) {
      insertTable()

      assertEquals(
        """
        > |   |   |
        > |---|---|
        > |   |   |
        """.trimIndent() + "\n",
        myFixture.editor.document.text,
      )
    }
  }

  @Test
  fun `test insert table after a blockquote line`(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "> previous\n> <caret>")

    withContext(Dispatchers.EDT) {
      insertTable()

      assertEquals(
        """
        > previous
        >${" "}
        > |   |   |
        > |---|---|
        > |   |   |
        """.trimIndent() + "\n",
        myFixture.editor.document.text,
      )
    }
  }

  private fun doTest(content: String, expected: String, count: Int = 1, shift: Boolean = false): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      repeat(count) {
        when {
          shift -> myFixture.executeAction("EditorStartNewLine")
          else -> myFixture.typeInEditor("\n")
        }
      }
      myFixture.checkResult(expected)
    }
  }

  private fun insertTable() {
    UiInterceptors.register(object : UiInterceptors.UiInterceptor<JBPopup>(JBPopup::class.java) {
      override fun doIntercept(popup: JBPopup) {
        val grid = checkNotNull(UIUtil.findComponentOfType(popup.content, InsertEmptyTableAction.TableGridComponent::class.java))
        grid.actionMap.get("selectRight").actionPerformed(ActionEvent(grid, 0, ""))
        grid.actionMap.get("selectDown").actionPerformed(ActionEvent(grid, 0, ""))
        grid.actionMap.get("confirmSelection").actionPerformed(ActionEvent(grid, 0, ""))
      }
    })
    myFixture.executeAction("Markdown.InsertEmptyTable")
  }

}
