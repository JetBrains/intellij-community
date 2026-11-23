// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.executeAction
import org.junit.jupiter.api.Test

@TestApplication
@Suppress("MarkdownIncorrectTableFormatting")
class MarkdownTableTabTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test single tab forward`() {
    // language=Markdown
    val before = """
    | none | none |
    |------|------|
    | some<caret> | some |
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none |
    |------|------|
    | some | <caret>some |
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `test multiple tabs forward`() {
    // language=Markdown
    val before = """
    | none | none | none |
    |------|------|------|
    | some<caret> | some | some |
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none | none |
    |------|------|------|
    | some | some | <caret>some |
    """.trimIndent()
    doTest(before, after, count = 2)
  }

  @Test
  fun `test multiple tabs forward to next row`() {
    // language=Markdown
    val before = """
    | none | none | none |
    |------|------|------|
    | some<caret> | some | some |
    | some | some | some |
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none | none |
    |------|------|------|
    | some | some | some |
    | some | some | <caret>some |
    """.trimIndent()
    doTest(before, after, count = 5)
  }

  @Test
  fun `test single tab backward`() {
    // language=Markdown
    val before = """
    | none | none |
    |------|------|
    | some | some<caret> |
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none |
    |------|------|
    | some<caret> | some |
    """.trimIndent()
    doTest(before, after, forward = false)
  }

  @Test
  fun `test multiple tabs backward`() {
    // language=Markdown
    val before = """
    | none | none | none |
    |------|------|------|
    | some | some | some<caret> |
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none | none |
    |------|------|------|
    | some<caret> | some | some |
    """.trimIndent()
    doTest(before, after, count = 2, forward = false)
  }

  @Test
  fun `test multiple tabs backward to previous row`() {
    // language=Markdown
    val before = """
    | none | none | none |
    |------|------|------|
    | some | some | some |
    | some | some | some<caret> |
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none | none |
    |------|------|------|
    | some<caret> | some | some |
    | some | some | some |
    """.trimIndent()
    doTest(before, after, count = 5, forward = false)
  }

  @Test
  fun `test multiple tabs forward in empty cells`() {
    // language=Markdown
    val before = """
    ||||
    |-|-|-|
    |<caret>|||
    """.trimIndent()
    // language=Markdown
    val after = """
    ||||
    |-|-|-|
    |||<caret>|
    """.trimIndent()
    doTest(before, after, count = 2)
  }

  private fun doTest(content: String, expected: String, count: Int = 1, forward: Boolean = true): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      repeat(count) {
        when {
          forward -> myFixture.executeAction("EditorTab")
          else -> myFixture.executeAction("EditorUnindentSelection")
        }
      }
      myFixture.checkResult(expected)
    }
  }
}
