// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.markdown.frontend.actions.InsertEmptyTableAction
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.ui.UiInterceptors
import com.intellij.util.ui.UIUtil
import org.intellij.plugins.markdown.lang.formatter.settings.TableStyle
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.event.ActionEvent

@RunWith(JUnit4::class)
@Suppress("MarkdownIncorrectTableFormatting", "MarkdownNoTableBorders")
class MarkdownTableEnterTest: LightPlatformCodeInsightTestCase() {
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
    withTableStyle(project, TableStyle.COMPACT) {
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
    withTableStyle(project, TableStyle.TIGHT) {
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
  fun `test insert table inside blockquote`() {
    configureFromFileText("some.md", "> <caret>")

    insertTable()

    assertEquals(
      """
      > |   |   |
      > |---|---|
      > |   |   |
      """.trimIndent() + "\n",
      editor.document.text,
    )
  }

  @Test
  fun `test insert table after a blockquote line`() {
    configureFromFileText("some.md", "> previous\n> <caret>")

    insertTable()

    assertEquals(
      """
      > previous
      >${" "}
      > |   |   |
      > |---|---|
      > |   |   |
      """.trimIndent() + "\n",
      editor.document.text,
    )
  }

  private fun doTest(content: String, expected: String, count: Int = 1, shift: Boolean = false) {
    configureFromFileText("some.md", content)
    repeat(count) {
      when {
        shift -> executeAction("EditorStartNewLine")
        else -> type("\n")
      }
    }
    checkResultByText(expected)
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
    executeAction("Markdown.InsertEmptyTable")
  }

}
