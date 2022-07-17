// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.testFramework.LightPlatformCodeInsightTestCase

class MarkdownTableColumnExpandTest: LightPlatformCodeInsightTestCase() {
  fun `test right after cell content`() {
    // language=Markdown
    doTest(
      """
      | none | none |
      |------|------|
      | a   <caret> | asd  |
      """.trimIndent(),
      """
      | none  | none |
      |-------|------|
      | a    <caret> | asd  |
      """.trimIndent()
    )
  }

  fun `test between spaces on the right side`() {
    // language=Markdown
    doTest(
      """
      | none | none |
      |------|------|
      |  a   <caret>   | asd  |
      """.trimIndent(),
      """
      | none  | none |
      |-------|------|
      | a    <caret> | asd  |
      """.trimIndent()
    )
  }

  fun `test just before right pipe`() {
    // language=Markdown
    doTest(
      """
      | none | none |
      |------|------|
      |  a   <caret>| asd  |
      """.trimIndent(),
      """
      | none  | none |
      |-------|------|
      | a    <caret> | asd  |
      """.trimIndent()
    )
  }

  fun `test in separator`() {
    doTest(
      """
      | none | none |
      |--<caret>----|------|
      |  a   | asd  |
      """.trimIndent(),
      """
      | none  | none |
      |---<caret>----|------|
      | a     | asd  |
      """.trimIndent(),
      string = "-"
    )
  }

  fun `test in separator with colon`() {
    doTest(
      """
      | none | none |
      |<caret>------|------|
      | a    | asd  |
      """.trimIndent(),
      """
      | none  | none |
      |:<caret>------|------|
      | a     | asd  |
      """.trimIndent(),
      string = ":"
    )
  }

  fun `test with right alignment`() {
    // language=Markdown
    val before = """
    |  right<caret>    |
    | ---:  |
    | some    |
    """.trimIndent()
    // language=Markdown
    val after = """
    | right <caret> |
    |-------:|
    |   some |
    """.trimIndent()
    doTest(before, after)
  }

  fun `test typing with right alignment`() {
    // language=Markdown
    val before = """
    | right |
    |------:|
    | <caret>      |
    """.trimIndent()
    // language=Markdown
    val after = """
    | right |
    |------:|
    |  some<caret> |
    """.trimIndent()
    doTest(before, after, string = "some")
  }

  fun `test typing with right alignment and spaces`() {
    // language=Markdown
    val before = """
    | right |
    |------:|
    | <caret>      |
    """.trimIndent()
    // language=Markdown
    val after = """
    |        right |
    |-------------:|
    | some content<caret> |
    """.trimIndent()
    doTest(before, after, string = "some content")
  }

  fun `test typing with left alignment`() {
    // language=Markdown
    val before = """
    | left |
    |-----:|
    |   <caret>   |
    """.trimIndent()
    // language=Markdown
    val after = """
    | left |
    |-----:|
    | some<caret> |
    """.trimIndent()
    doTest(before, after, string = "some")
  }

  fun `test typing with left alignment and spaces`() {
    // language=Markdown
    val before = """
    | left |
    |-----:|
    |   <caret>   |
    """.trimIndent()
    // language=Markdown
    val after = """
    |         left |
    |-------------:|
    | some content<caret> |
    """.trimIndent()
    doTest(before, after, string = "some content")
  }

  fun `test typing with center alignment`() {
    // language=Markdown
    val before = """
    | center |
    |:------:|
    | <caret>   |
    """.trimIndent()
    // language=Markdown
    val after = """
    | center |
    |:------:|
    |  some<caret>  |
    """.trimIndent()
    doTest(before, after, string = "some")
  }

  fun `test typing with center alignment and ends with space`() {
    // language=Markdown
    val before = """
    | center |
    |:------:|
    | <caret>   |
    """.trimIndent()
    // language=Markdown
    val after = """
    | center |
    |:------:|
    | ssome <caret> |
    """.trimIndent()
    doTest(before, after, string = "ssome ")
  }

  private fun doTest(content: String, expected: String, count: Int = 1, string: String = " ") {
    TableTestUtils.runWithChangedSettings(project) {
      configureFromFileText("some.md", content)
      type(string.repeat(count))
      checkResultByText(expected)
    }
  }
}
