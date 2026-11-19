// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.backspace
import org.intellij.plugins.markdown.editor.delete
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
@Suppress("MarkdownIncorrectTableFormatting")
class MarkdownTableColumnShrinkWithFullWidthTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @TestDisposable
  lateinit var disposable: Disposable

  @BeforeEach
  fun enableTableReformatting() {
    setupTableReformatting(disposable)
    setupTableCellsAlignedVisually(false, disposable)
  }

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
      action = { myFixture.delete() }
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
      action = { myFixture.delete() }
    )
  }

  private fun doTest(content: String, expected: String, count: Int = 1, action: () -> Unit = { myFixture.backspace() }): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      repeat(count) {
        action()
      }
      myFixture.checkResult(expected)
    }
  }
}
