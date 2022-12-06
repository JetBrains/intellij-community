// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@Suppress("MarkdownIncorrectTableFormatting", "MarkdownNoTableBorders")
class MarkdownTableEnterTest: LightPlatformCodeInsightTestCase() {
  @Test
  fun `test single enter inside cell`() {
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
    | some<br/><caret> | some |
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `test double enter inside cell`() {
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
    | some
    <caret>| some |
    """.trimIndent()
    doTest(before, after, count = 2)
  }

  @Test
  fun `test single enter at the row end`() {
    // language=Markdown
    val before = """
    | none | none |
    |------|------|
    | some | some |<caret>
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none |
    |------|------|
    | some | some |
    
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `test shift enter at the row end`() {
    // language=Markdown
    val before = """
    | none | none |
    |------|------|
    | some | some |<caret>
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none |
    |------|------|
    | some | some |
    |      |      |<caret>
    """.trimIndent()
    doTest(before, after, shift = true)
  }

  @Test
  fun `test shift enter at the separator row end`() {
    // language=Markdown
    val before = """
    | none | none |
    |------|------|<caret>
    | some | some |
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none |
    |------|------|
    |      |      |<caret>
    | some | some |
    """.trimIndent()
    doTest(before, after, shift = true)
  }

  @Test
  fun `test shift enter at the header row end`() {
    // language=Markdown
    val before = """
    | none | none |<caret>
    |------|------|
    | some | some |
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none |
    <caret>
    |------|------|
    | some | some |
    """.trimIndent()
    doTest(before, after, shift = true)
  }

  private fun doTest(content: String, expected: String, count: Int = 1, shift: Boolean = false) {
    configureFromFileText("some.md", content)
    repeat(count) {
      when {
        shift -> executeAction("EditorStartNewLine")
        else -> type("\n")
      }
    }
    checkResultByText(expected)
  }
}
