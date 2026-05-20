// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.intellij.plugins.markdown.MarkdownBundle
import org.junit.Test

@Suppress("MarkdownIncorrectTableFormatting")
class MarkdownInsertTableColumnIntentionTest: LightPlatformCodeInsightFixture4TestCase() {
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
    // The display-aligned post-action text below renders correctly in editors that have
    // character-grid mode active (CJK chars at 2 grid cells, ASCII at 1). The bug the user
    // reported — pipes drifting left on CJK rows in the IntentionPreview popup — is fixed
    // by enabling grid mode on the preview popup's editor (see MarkdownCharacterGridEditorFactoryListener),
    // not by changing the action's output. This test pins the unchanged output so that the
    // grid-mode listener stays the only moving piece for the fix.
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

  private fun doTest(content: String, after: String, insertRight: Boolean) {
    myFixture.configureByText("some.md", content)
    val key = when {
      insertRight -> "markdown.insert.table.column.to.the.right.intention.text"
      else -> "markdown.insert.table.column.to.the.left.intention.text"
    }
    val intention = myFixture.findSingleIntention(MarkdownBundle.message(key))
    myFixture.launchAction(intention)
    assertEquals(after, myFixture.editor.document.text)
  }
}
