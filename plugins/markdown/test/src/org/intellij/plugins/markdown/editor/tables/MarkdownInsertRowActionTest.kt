// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

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

  private fun doTest(content: String, expected: String) {
    configureFromFileText("some.md", content)
    executeAction("Markdown.Table.InsertRow.InsertBelow")
    checkResultByText(expected)
  }
}
