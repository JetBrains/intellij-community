// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@Suppress("MarkdownIncorrectTableFormatting")
class MarkdownTableTabTest: LightPlatformCodeInsightTestCase() {
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

  private fun doTest(content: String, expected: String, count: Int = 1, forward: Boolean = true) {
    TableTestUtils.runWithChangedSettings(project) {
      configureFromFileText("some.md", content)
      repeat(count) {
        when {
          forward -> executeAction("EditorTab")
          else -> executeAction("EditorUnindentSelection")
        }
      }
      checkResultByText(expected)
    }
  }
}
