// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@Suppress("MarkdownIncorrectTableFormatting")
class MarkdownTableColumnShrinkWithFullWidthTest: LightPlatformCodeInsightTestCase() {
  @Test
  fun `test backspace cjk character`() {
    // language=Markdown
    doTest(
      """
      | 名字     | 年龄  |
      |----------|-------|
      | 投放账号<caret> | 3 . * |
      """.trimIndent(),
      """
      | 名字   | 年龄  |
      |--------|-------|
      | 投放账<caret> | 3 . * |
      """.trimIndent()
    )
  }

  @Test
  fun `test backspace latin character`() {
    // language=Markdown
    doTest(
      """
      | f   | fef | fjeijjkj                 |
      |-----|-----|--------------------------|
      | dwd | dwd | dhwhhjhjhjhj<caret>hjhjhjhb bjb |
      """.trimIndent(),
      """
      | f   | fef | fjeijjkj                |
      |-----|-----|-------------------------|
      | dwd | dwd | dhwhhjhjhjh<caret>hjhjhjhb bjb |
      """.trimIndent()
    )
  }

  @Test
  fun `test forward delete cjk character`() {
    // language=Markdown
    doTest(
      """
      | 名字     | 年龄  |
      |----------|-------|
      | <caret>投放账号 | 3 . * |
      """.trimIndent(),
      """
      | 名字   | 年龄  |
      |--------|-------|
      | <caret>放账号 | 3 . * |
      """.trimIndent(),
      action = { delete() }
    )
  }

  @Test
  fun `test forward delete latin character`() {
    // language=Markdown
    doTest(
      """
      | f   | fef | fjeijjkj                 |
      |-----|-----|--------------------------|
      | dwd | dwd | dhwhhjhjhjhj<caret>hjhjhjhb bjb |
      """.trimIndent(),
      """
      | f   | fef | fjeijjkj                |
      |-----|-----|-------------------------|
      | dwd | dwd | dhwhhjhjhjhj<caret>jhjhjhb bjb |
      """.trimIndent(),
      action = { delete() }
    )
  }

  private fun doTest(content: String, expected: String, count: Int = 1, action: () -> Unit = { backspace() }) {
    configureFromFileText("some.md", content)
    repeat(count) {
      action()
    }
    checkResultByText(expected)
  }
}
