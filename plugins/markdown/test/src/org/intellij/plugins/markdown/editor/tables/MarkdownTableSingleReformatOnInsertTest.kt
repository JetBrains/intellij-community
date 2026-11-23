// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.intellij.plugins.markdown.editor.tables.TableFormattingUtils.reformatColumnOnChange
import org.intellij.plugins.markdown.editor.tables.TableUtils.columnsIndices
import org.intellij.plugins.markdown.lang.formatter.settings.TableStyle
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
@Suppress("MarkdownIncorrectTableFormatting")
class MarkdownTableSingleReformatOnInsertTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test single column without alignment`() {
    // language=Markdown
    val before = """
    |       none |
    | ---  |
    | some   |
    """.trimIndent()
    // language=Markdown
    val after = """
    | none |
    |------|
    | some |
    """.trimIndent()
    doTest(before, after, trimToMaxContent = true)
  }

  @Test
  fun `test single column without alignment and padding`() {
    // language=Markdown
    val before = """
    |none|
    |--- |
    |some|
    """.trimIndent()
    // language=Markdown
    val after = """
    | none |
    |------|
    | some |
    """.trimIndent()
    doTest(before, after, trimToMaxContent = true)
  }

  @Test
  fun `test single column with center alignment`() {
    // language=Markdown
    val before = """
    |       none |
    | :---:  |
    | some |
    """.trimIndent()
    // language=Markdown
    val after = """
    |    none    |
    |:----------:|
    |    some    |
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `test single column with center alignment and caret`() {
    // language=Markdown
    val before = """
    |       none<caret> |
    | :---:  |
    | some |
    """.trimIndent()
    // language=Markdown
    val after = """
    |    none<caret>    |
    |:----------:|
    |    some    |
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `test single column with center alignment and multiple carets`() {
    // language=Markdown
    val before = """
    |       none<caret> |
    | :---:  |
    | <caret>so<caret>me |
    """.trimIndent()
    // language=Markdown
    val after = """
    |    none<caret>    |
    |:----------:|
    |    <caret>so<caret>me    |
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `test compact empty cell keeps one padding space around caret`(): Unit = timeoutRunBlocking {
    withTableStyle(myFixture.project, TableStyle.COMPACT) {
      myFixture.configureByText(
        "some.md",
        """
        | a |
        | --- |
        | <caret> |
        """.trimIndent()
      )
      WriteCommandAction.runWriteCommandAction(myFixture.project) {
        val table = TableUtils.findTable(myFixture.file, 0)!!
        table.reformatColumnOnChange(
          myFixture.editor.document,
          myFixture.editor.caretModel.allCarets,
          0,
          trimToMaxContent = true,
          tableStyle = TableStyle.COMPACT,
        )
        PsiDocumentManager.getInstance(myFixture.project).commitDocument(myFixture.editor.document)
      }
      myFixture.checkResult(
        """
        | a |
        | --- |
        | <caret>|
        """.trimIndent()
      )
    }
  }

  @Test
  fun `test multiple columns with right alignment and multiple carets`() {
    // language=Markdown
    val before = """
    |  right<caret>    |    none |
    | ---:  | ----  |
    | <caret>so<caret>me    |    some |
    """.trimIndent()
    // language=Markdown
    val after = """
    |     right<caret> | none    |
    |----------:|---------|
    |      <caret>so<caret>me | some    |
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `test reformatting is not confused by escaped pipe`() {
    // language=Markdown
    val before = """
    | none |
    | :---:  |
    | A\|B<caret> |
    """.trimIndent()
    // language=Markdown
    val after = """
    | none |
    |:----:|
    | A\|B<caret> |
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `test reformatting is not confused by escaped pipe in table with multiple columns`() {
    // language=Markdown
    val before = """
    | none |    A\|B |
    | :---:  |  ---: |
    | A\|B<caret> | A\|B\|\|\|B<caret> |
    """.trimIndent()
    // language=Markdown
    val after = """
    | none |        A\|B |
    |:----:|------------:|
    | A\|B<caret> | A\|B\|\|\|B<caret> |
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `test reformatting is not confused by escaped pipe inside code span`() {
    // language=Markdown
    val before = """
    | none |
    | :---:  |
    | `A\|B`<caret> |
    """.trimIndent()
    // language=Markdown
    val after = """
    |  none  |
    |:------:|
    | `A\|B`<caret> |
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `test large column reformat uses bulk update mode`(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", createLargeSingleColumnTable())
    var bulkUpdateSeen = false
    myFixture.editor.document.addDocumentListener(object : DocumentListener {
      override fun beforeDocumentChange(event: DocumentEvent) {
        bulkUpdateSeen = bulkUpdateSeen || event.document.isInBulkUpdate
      }
    }, myFixture.testRootDisposable)

    WriteCommandAction.runWriteCommandAction(myFixture.project) {
      val table = TableUtils.findTable(myFixture.file, 0)!!
      table.reformatColumnOnChange(
        myFixture.editor.document,
        myFixture.editor.caretModel.allCarets,
        0,
        trimToMaxContent = true,
        tableStyle = TableStyle.ALIGNED,
      )
      PsiDocumentManager.getInstance(myFixture.project).commitDocument(myFixture.editor.document)
    }

    assertTrue(bulkUpdateSeen)
    assertFalse(myFixture.editor.document.isInBulkUpdate)
    myFixture.checkResult(createLargeSingleColumnTableAfterReformat())
  }

  private fun doTest(before: String, after: String, trimToMaxContent: Boolean = false): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", before)
    WriteCommandAction.runWriteCommandAction(myFixture.project) {
      val table = TableUtils.findTable(myFixture.file, 0)!!
      for (columnIndex in table.columnsIndices) {
        table.reformatColumnOnChange(
          myFixture.editor.document,
          myFixture.editor.caretModel.allCarets,
          columnIndex,
          trimToMaxContent,
          tableStyle = TableStyle.ALIGNED,
        )
        PsiDocumentManager.getInstance(myFixture.project).commitDocument(myFixture.editor.document)
      }
    }
    myFixture.checkResult(after)
  }

  private fun createLargeSingleColumnTable(): String {
    return buildList {
      add("|header|")
      add("|---|")
      repeat(101) { index ->
        add(
          if (index == 100) {
            "|value <caret>$index|"
          }
          else {
            "|value $index|"
          }
        )
      }
    }.joinToString("\n")
  }

  private fun createLargeSingleColumnTableAfterReformat(): String {
    return buildList {
      add("| header    |")
      add("|-----------|")
      repeat(101) { index ->
        add(
          if (index == 100) {
            "| value <caret>$index |"
          }
          else {
            "| ${"value $index".padEnd(10)}|"
          }
        )
      }
    }.joinToString("\n")
  }
}
