// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.formatter.settings.TableStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@Suppress("MarkdownIncorrectTableFormatting")
@TestApplication
class MarkdownInsertTableColumnIntentionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `insert column to the right in ASCII table aligns pipes`() {
    // Sanity check: the intention adds a ":--"-aligned separator cell (it uses CellAlignment.LEFT)
    // and pipes line up character-wise across all rows. ASCII baseline so the CJK tests below have
    // a comparable reference.
    // language=Markdown
    val before = """
    | a | b |
    |---|---|
    | 1<caret> | 2 |
    """.trimIndent()
    // language=Markdown
    val after = """
    | a |   | b |
    |---|:--|---|
    | 1 |   | 2 |
    """.trimIndent()
    doTest(before, after, insertRight = true)
  }

  @Test
  fun `insert column to the right in CJK table produces display-aligned text`() {
    // Visual alignment fixes the rendered output without changing the formatter's character-counted text.
    // language=Markdown
    val before = """
    | 名字 | 年龄 |
    |------|------|
    | 张<caret>三 | 25   |
    """.trimIndent()
    // language=Markdown
    val after = """
    | 名字 |   | 年龄 |
    |------|:--|------|
    | 张三 |   | 25   |
    """.trimIndent()
    doTest(before, after, insertRight = true)
  }

  @Test
  fun `insert column to the left in CJK table produces display-aligned text`() {
    // language=Markdown
    val before = """
    | 名字 | 年龄 |
    |------|------|
    | 张三 | 2<caret>5   |
    """.trimIndent()
    // language=Markdown
    val after = """
    | 名字 |   | 年龄 |
    |------|:--|------|
    | 张三 |   | 25   |
    """.trimIndent()
    doTest(before, after, insertRight = false)
  }

  @Test
  fun `insert column to the right in compact table intention`() {
    withTableStyle(myFixture.project, TableStyle.COMPACT) {
      doTest(
        """
        | a | b |
        | --- | --- |
        | 1<caret> | 2 |
        """.trimIndent(),
        """
        | a | | b |
        | --- | :--- | --- |
        | 1 | | 2 |
        """.trimIndent(),
        insertRight = true
      )
    }
  }

  @Test
  fun `insert column to the left in tight table intention`() {
    withTableStyle(myFixture.project, TableStyle.TIGHT) {
      doTest(
        """
        |a|<caret>b|
        |---|---|
        |1|2|
        """.trimIndent(),
        """
        |a||b|
        |---|:---|---|
        |1||2|
        """.trimIndent(),
        insertRight = false
      )
    }
  }

  private fun doTest(content: String, after: String, insertRight: Boolean) {
    myFixture.configureByText("some.md", content)
    val key = when {
      insertRight -> "markdown.insert.table.column.to.the.right.intention.text"
      else -> "markdown.insert.table.column.to.the.left.intention.text"
    }
    timeoutRunBlocking {
      withContext(Dispatchers.EDT) {
        EditorTestUtil.configureSoftWraps(myFixture.editor, 6)
        assertThat(myFixture.editor.softWrapModel.getSoftWrapsForRange(0, myFixture.editor.document.textLength)).isEmpty()
        val intention = myFixture.findSingleIntention(MarkdownBundle.message(key))
        myFixture.launchAction(intention)
        assertEquals(after, myFixture.editor.document.text)
      }
    }
  }

}
