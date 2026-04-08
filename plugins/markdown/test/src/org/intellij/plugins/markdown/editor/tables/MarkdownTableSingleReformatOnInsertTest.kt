// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.editor.tables.TableFormattingUtils.reformatColumnOnChange
import org.intellij.plugins.markdown.editor.tables.TableUtils.columnsIndices

@Suppress("MarkdownIncorrectTableFormatting")
class MarkdownTableSingleReformatOnInsertTest: LightPlatformCodeInsightTestCase() {
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

  fun `test large column reformat uses bulk update mode`() {
    configureFromFileText("some.md", createLargeSingleColumnTable())
    val table = TableUtils.findTable(file, 0)!!
    var bulkUpdateSeen = false
    editor.document.addDocumentListener(object : DocumentListener {
      override fun beforeDocumentChange(event: DocumentEvent) {
        bulkUpdateSeen = bulkUpdateSeen || event.document.isInBulkUpdate
      }
    }, testRootDisposable)

    runWriteActionAndWait {
      table.reformatColumnOnChange(editor.document, editor.caretModel.allCarets, 0, trimToMaxContent = true)
      commitDocument(editor.document)
    }

    assertTrue(bulkUpdateSeen)
    assertFalse(editor.document.isInBulkUpdate)
    checkResultByText(createLargeSingleColumnTableAfterReformat())
  }

  private fun doTest(before: String, after: String, trimToMaxContent: Boolean = false) {
    configureFromFileText("some.md", before)
    val table = TableUtils.findTable(file, 0)!!
    runWriteActionAndWait {
      for (columnIndex in table.columnsIndices) {
        table.reformatColumnOnChange(editor.document, editor.caretModel.allCarets, columnIndex, trimToMaxContent)
        commitDocument(editor.document)
      }
    }
    checkResultByText(after)
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
