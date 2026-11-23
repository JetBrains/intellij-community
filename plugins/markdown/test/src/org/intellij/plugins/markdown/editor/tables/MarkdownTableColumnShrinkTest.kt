// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
@Suppress("MarkdownIncorrectTableFormatting")
class MarkdownTableColumnShrinkTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @TestDisposable
  lateinit var disposable: Disposable

  @BeforeEach
  fun enableTableReformatting() {
    setupTableReformatting(disposable)
  }

  @Test
  fun `test right after cell content`() {
    // language=Markdown
    doTest(
      """
      | none  | none |
      |-------|------|
      |  a <caret>   | asd  |
      """.trimIndent(),
      """
      | none | none |
      |------|------|
      |  a<caret>   | asd  |
      """.trimIndent()
    )
  }

  @Test
  fun `test between spaces on the right side`() {
    // language=Markdown
    doTest(
      """
      | none  | none |
      |-------|------|
      |  a   <caret> | asd  |
      """.trimIndent(),
      """
      | none | none |
      |------|------|
      |  a  <caret> | asd  |
      """.trimIndent()
    )
  }

  @Test
  fun `test just before right pipe`() {
    // language=Markdown
    doTest(
      """
      | none  | none |
      |-------|------|
      |  a    <caret>| asd  |
      """.trimIndent(),
      """
      | none | none |
      |------|------|
      |  a   <caret>| asd  |
      """.trimIndent()
    )
  }

  @Test
  fun `test right before cell content`() {
    // language=Markdown
    doTest(
      """
      | none  | none |
      |-------|------|
      |   <caret>a   | asd  |
      """.trimIndent(),
      """
      | none | none |
      |------|------|
      |  <caret>a   | asd  |
      """.trimIndent()
    )
  }

  @Test
  fun `test just after left pipe`() {
    // language=Markdown
    doTest(
      """
      | none  | none |
      |-------|------|
      | <caret>  a   | asd  |
      """.trimIndent(),
      """
      | none | none |
      |------|------|
      |<caret>  a   | asd  |
      """.trimIndent()
    )
  }

  @Test
  fun `test in separator`() {
    doTest(
      """
      | none  | none |
      |---<caret>----|------|
      |  a    | asd  |
      """.trimIndent(),
      """
      | none | none |
      |--<caret>----|------|
      |  a   | asd  |
      """.trimIndent()
    )
  }

  @Test
  fun `test in separator with colon`() {
    doTest(
      """
      | none  | none |
      |:<caret>------|------|
      | a     | asd  |
      """.trimIndent(),
      """
      | none | none |
      |<caret>------|------|
      | a    | asd  |
      """.trimIndent()
    )
  }

  @Test
  fun `test typing to single character width cell`() {
    doTest(
      """
      |     |
      |-----|
      | 123<caret> |
      """.trimIndent(),
      """
      | |
      |-|
      |<caret> |
      """.trimIndent(),
      count = 4
    )
  }

  @Test
  fun `test typing to empty cell`() {
    doTest(
      """
      | |
      |-|
      | <caret>|
      """.trimIndent(),
      """
      | |
      |-|
      |<caret>|
      """.trimIndent()
    )
  }

  @Test
  fun `test typing to empty cell with non empty header`() {
    doTest(
      """
      |a|
      |-|
      | <caret>|
      """.trimIndent(),
      """
      |a|
      |-|
      |<caret>|
      """.trimIndent()
    )
  }

  private fun doTest(content: String, expected: String, count: Int = 1): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      repeat(count) {
        myFixture.backspace()
      }
      myFixture.checkResult(expected)
    }
  }
}
