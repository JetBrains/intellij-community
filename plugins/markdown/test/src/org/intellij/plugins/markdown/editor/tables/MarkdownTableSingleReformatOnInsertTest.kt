// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.openapi.application.runWriteActionAndWait
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
}
