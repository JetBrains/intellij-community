// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.editor.tables.actions.TableActionKeys
import org.intellij.plugins.markdown.editor.tables.actions.TableActionPlaces
import org.intellij.plugins.markdown.editor.tables.actions.row.InsertRowAction
import org.intellij.plugins.markdown.lang.formatter.settings.TableStyle
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.lang.ref.WeakReference

@RunWith(JUnit4::class)
@Suppress("MarkdownIncorrectTableFormatting")
class MarkdownInsertRowActionTest: LightPlatformCodeInsightTestCase() {
  @Test
  fun `insert row below in ASCII table keeps pipes aligned`() {
    // language=Markdown
    doTest(
      """
      | A  | B  | C  |
      |----|----|----|
      | 11<caret> | 12 | 13 |
      """.trimIndent(),
      """
      | A  | B  | C  |
      |----|----|----|
      | 11 | 12 | 13 |
      |    |    |    |
      """.trimIndent()
    )
  }

  @Test
  fun `insert row below in CJK table keeps pipes aligned`() {
    // Cells " 张三 " have textLength 4 but display width 6 (each CJK char is 2 columns wide).
    // The inserted empty row must use display widths so pipes line up with the header/separator.
    // language=Markdown
    doTest(
      """
      | 名字 | 年龄 |
      |------|------|
      | 张三<caret> | 25   |
      """.trimIndent(),
      """
      | 名字 | 年龄 |
      |------|------|
      | 张三 | 25   |
      |      |      |
      """.trimIndent()
    )
  }

  @Test
  fun `insert row below in block quote keeps marker`() {
    doTest(
      """
      > | A | B |
      > | --- | --- |
      > | 11<caret> | 12 |
      > | 21 | 22 |
      """.trimIndent(),
      """
      > | A | B |
      > | --- | --- |
      > | 11 | 12 |
      > |    |    |
      > | 21 | 22 |
      """.trimIndent()
    )
  }

  @Test
  fun `insert row above in block quote keeps marker`() {
    doTest(
      """
      > | A | B |
      > | --- | --- |
      > | 11<caret> | 12 |
      > | 21 | 22 |
      """.trimIndent(),
      """
      > | A | B |
      > | --- | --- |
      > |    |    |
      > | 11 | 12 |
      > | 21 | 22 |
      """.trimIndent(),
      actionId = "Markdown.Table.InsertRow.InsertAbove",
    )
  }

  @Test
  fun `insert row below in compact table`() {
    withTableStyle(project, TableStyle.COMPACT) {
      doTest(
        """
        | A | B | C |
        | --- | --- | --- |
        | 11<caret> | 12 | 13 |
        """.trimIndent(),
        """
        | A | B | C |
        | --- | --- | --- |
        | 11 | 12 | 13 |
        | | | |
        """.trimIndent()
      )
    }
  }

  @Test
  fun `insert row above in compact table`() {
    withTableStyle(project, TableStyle.COMPACT) {
      doTest(
        """
        | A | B | C |
        | --- | --- | --- |
        | 11<caret> | 12 | 13 |
        """.trimIndent(),
        """
        | A | B | C |
        | --- | --- | --- |
        | | | |
        | 11 | 12 | 13 |
        """.trimIndent(),
        actionId = "Markdown.Table.InsertRow.InsertAbove",
      )
    }
  }

  @Test
  fun `insert row below in tight table`() {
    withTableStyle(project, TableStyle.TIGHT) {
      doTest(
        """
        |A|B|C|
        |---|---|---|
        |11<caret>|12|13|
        """.trimIndent(),
        """
        |A|B|C|
        |---|---|---|
        |11|12|13|
        ||||
        """.trimIndent()
      )
    }
  }

  @Test
  fun `insert row above in tight table`() {
    withTableStyle(project, TableStyle.TIGHT) {
      doTest(
        """
        |A|B|C|
        |---|---|---|
        |11<caret>|12|13|
        """.trimIndent(),
        """
        |A|B|C|
        |---|---|---|
        ||||
        |11|12|13|
        """.trimIndent(),
        actionId = "Markdown.Table.InsertRow.InsertAbove",
      )
    }
  }

  @Test
  fun `insert row actions are disabled for the header row in the table bar`() {
    configureFromFileText(
      "some.md",
      """
      | Header | Value |
      |--------|-------|
      | data   | data  |
      """.trimIndent()
    )
    val table = requireNotNull(PsiTreeUtil.findChildOfType(file, MarkdownTable::class.java))
    val headerRow = requireNotNull(table.headerRow)
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(CommonDataKeys.EDITOR, editor)
      .add(CommonDataKeys.PSI_FILE, file)
      .add(CommonDataKeys.CARET, editor.caretModel.currentCaret)
      .add(TableActionKeys.ELEMENT, WeakReference(headerRow))
      .build()

    for (action in listOf(InsertRowAction.InsertAbove(), InsertRowAction.InsertBelow())) {
      val event = AnActionEvent.createEvent(
        action,
        dataContext,
        null,
        TableActionPlaces.TABLE_INLAY_TOOLBAR,
        ActionUiKind.TOOLBAR,
        null,
      )
      action.update(event)
      assertFalse(event.presentation.isEnabledAndVisible)
    }
  }

  private fun doTest(content: String, expected: String, actionId: String = "Markdown.Table.InsertRow.InsertBelow") {
    configureFromFileText("some.md", content)
    executeAction(actionId)
    checkResultByText(expected)
  }

}
