// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.application.options.CodeStyle
import com.intellij.application.options.codeStyle.excludedFiles.GlobPatternDescriptor
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.lang.formatter.settings.TableStyle
import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.datatransfer.StringSelection

@RunWith(JUnit4::class)
@Suppress("MarkdownIncorrectTableFormatting")
class MarkdownTableTypingTest: LightPlatformCodeInsightTestCase() {
  @Before
  fun enableTableReformatting() {
    setupTableReformatting(testRootDisposable)
  }

  @Test
  fun `test typing in non-last column reformats the table`() {
    // language=Markdown
    val before = """
    | a |malformed| c |
    |---|---|---|
    | 1<caret> |y| 2 |
    """.trimIndent()

    // language=Markdown
    val after = """
    | a  | malformed | c |
    |----|-----------|---|
    | 1x | y         | 2 |
    """.trimIndent()
    doTest(before, after, 1, "x")
  }

  @Test
  fun `test typing in last column reformats the table`() {
    // language=Markdown
    val before = """
    | a |malformed| c |
    |---|---|---|
    | 1 |y| 2<caret> |
    """.trimIndent()

    // language=Markdown
    val after = """
    | a | malformed | c  |
    |---|-----------|----|
    | 1 | y         | 2x |
    """.trimIndent()
    doTest(before, after, 1, "x")
  }

  @Test
  fun `test typing in compact table`() {
    withTableStyle(project, TableStyle.COMPACT) {
      doTest(
        """
        | a |malformed| c |
        |---|---|---|
        | 1<caret> |y| 2 |
        """.trimIndent(),
        """
        | a | malformed | c |
        | --- | --- | --- |
        | 1x | y | 2 |
        """.trimIndent(),
        string = "x"
      )
    }
  }

  @Test
  fun `test backspacing last content in compact empty cell`() {
    withTableStyle(project, TableStyle.COMPACT) {
      configureFromFileText(
        "some.md",
        """
        | a |
        | --- |
        | x<caret> |
        """.trimIndent()
      )
      backspace()
      checkResultByText(
        """
        | a |
        | --- |
        | <caret>|
        """.trimIndent()
      )
    }
  }

  @Test
  fun `test typing in tight table`() {
    withTableStyle(project, TableStyle.TIGHT) {
      doTest(
        """
        | a |malformed| c |
        |---|---|---|
        | 1<caret> |y| 2 |
        """.trimIndent(),
        """
        |a|malformed|c|
        |---|---|---|
        |1x|y|2|
        """.trimIndent(),
        string = "x"
      )
    }
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  fun `test typing in empty cell`() {
    // language=Markdown
    val before = """
    ||
    |-|
    |<caret>|
    """.trimIndent()
    // language=Markdown
    val after = """
    |      |
    |------|
    | some<caret> |
    """.trimIndent()
    doTest(before, after, string = "some")
  }

  @Test
  fun `test typing space in cell with two spaces`() {
    // language=Markdown
    val before = """
    |  |
    |--|
    | <caret> |
    """.trimIndent()
    // language=Markdown
    val after = """
    |   |
    |---|
    |  <caret> |
    """.trimIndent()
    doTest(before, after, string = " ")
  }

  @Test
  fun `test no column expand`() {
    // language=Markdown
    val content = """
    | some |
    |------|
    | <caret>     |
    """.trimIndent()
    // language=Markdown
    val expected = """
    | some |
    |------|
    | some<caret>     |
    """.trimIndent()
    configureFromFileText("some.md", content)
    runWithDisabledFormatting(file) {
      type("some")
      checkResultByText(expected)
    }
  }

  @Test
  fun `test no column shrink`() {
    // language=Markdown
    val content = """
    | some         |
    |--------------|
    | some text<caret>    |
    """.trimIndent()
    // language=Markdown
    val expected = """
    | some         |
    |--------------|
    | some tex<caret>    |
    """.trimIndent()
    configureFromFileText("some.md", content)
    runWithDisabledFormatting(file) {
      backspace()
      checkResultByText(expected)
    }
  }

  @Test
  fun `test no alignment correction`() {
    // language=Markdown
    val content = """
    | some content |
    |:------------:|
    | <caret>             |
    """.trimIndent()
    // language=Markdown
    val expected = """
    | some content |
    |:------------:|
    | some<caret>             |
    """.trimIndent()
    configureFromFileText("some.md", content)
    runWithDisabledFormatting(file) {
      type("some")
      checkResultByText(expected)
    }
  }

  @Test
  fun `test no column expand when reformat on type is disabled`() {
    // language=Markdown
    val content = """
    | some |
    |------|
    | <caret>     |
    """.trimIndent()
    // language=Markdown
    val expected = """
    | some |
    |------|
    | some<caret>     |
    """.trimIndent()
    configureFromFileText("some.md", content)
    runWithReformatOnTypeDisabled {
      type("some")
      checkResultByText(expected)
    }
  }

  @Test
  fun `test no column shrink when reformat on type is disabled`() {
    // language=Markdown
    val content = """
    | some         |
    |--------------|
    | some text<caret>    |
    """.trimIndent()
    // language=Markdown
    val expected = """
    | some         |
    |--------------|
    | some tex<caret>    |
    """.trimIndent()
    configureFromFileText("some.md", content)
    runWithReformatOnTypeDisabled {
      backspace()
      checkResultByText(expected)
    }
  }

  @Test
  fun `test no alignment correction when reformat on type is disabled`() {
    // language=Markdown
    val content = """
    | some content |
    |:------------:|
    | <caret>             |
    """.trimIndent()
    // language=Markdown
    val expected = """
    | some content |
    |:------------:|
    | some<caret>             |
    """.trimIndent()
    configureFromFileText("some.md", content)
    runWithReformatOnTypeDisabled {
      type("some")
      checkResultByText(expected)
    }
  }

  @Test
  fun `test pasting reformats the table`() {
    // language=Markdown
    val before = """
    | a | b |
    |---|---|
    | one<caret> | 2 |
    """.trimIndent()
    // language=Markdown
    val after = """
    | a      | b |
    |--------|---|
    | onexyz | 2 |
    """.trimIndent()
    configureFromFileText("some.md", before)
    CopyPasteManager.getInstance().setContents(StringSelection("xyz"))
    executeAction(IdeActions.ACTION_EDITOR_PASTE)
    checkResultByText(after)
  }

  @Test
  fun `test cutting reformats the table`() {
    // language=Markdown
    val before = """
    | a           | b |
    |-------------|---|
    | <selection>removed</selection>kept | 2 |
    """.trimIndent()
    // language=Markdown
    val after = """
    | a           | b |
    |-------------|---|
    | kept        | 2 |
    """.trimIndent()
    configureFromFileText("some.md", before)
    executeAction(IdeActions.ACTION_EDITOR_CUT)
    checkResultByText(after)
  }

  @Test
  fun `test duplicating reformats the table`() {
    // language=Markdown
    val before = """
    | a | b |
    |---|---|
    | <selection>one</selection> | 2 |
    """.trimIndent()
    // language=Markdown
    val after = """
    | a      | b |
    |--------|---|
    | oneone | 2 |
    """.trimIndent()
    configureFromFileText("some.md", before)
    executeAction(IdeActions.ACTION_EDITOR_DUPLICATE)
    checkResultByText(after)
  }

  @Test
  fun `test deleting to word start reformats the table`() {
    // language=Markdown
    val before = """
    | a            | b |
    |--------------|---|
    | kept removed<caret> | 2 |
    """.trimIndent()
    // language=Markdown
    val after = """
    | a            | b |
    |--------------|---|
    | kept         | 2 |
    """.trimIndent()
    configureFromFileText("some.md", before)
    executeAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_START)
    checkResultByText(after)
  }

  @Test
  fun `test no reformat on pasting when reformat on type is disabled`() {
    // language=Markdown
    val content = """
    | a | b |
    |---|---|
    | one<caret> | 2 |
    """.trimIndent()
    // language=Markdown
    val expected = """
    | a | b |
    |---|---|
    | onexyz | 2 |
    """.trimIndent()
    configureFromFileText("some.md", content)
    CopyPasteManager.getInstance().setContents(StringSelection("xyz"))
    runWithReformatOnTypeDisabled {
      executeAction(IdeActions.ACTION_EDITOR_PASTE)
      checkResultByText(expected)
    }
  }

  @Test
  fun `test pasting outside of a table keeps the text`() {
    configureFromFileText("some.md", "Some text<caret>")
    CopyPasteManager.getInstance().setContents(StringSelection("xyz"))
    executeAction(IdeActions.ACTION_EDITOR_PASTE)
    checkResultByText("Some textxyz")
  }

  private fun runWithReformatOnTypeDisabled(block: () -> Unit) {
    val settings = MarkdownCodeInsightSettings.getInstance()
    val old = settings.state.reformatTablesOnType
    settings.state.reformatTablesOnType = false
    try {
      block.invoke()
    }
    finally {
      settings.state.reformatTablesOnType = old
    }
  }

  private fun runWithDisabledFormatting(file: PsiFile, block: () -> Unit) {
    val settings = CodeStyle.getSettings(file)
    val old = settings.excludedFiles.descriptors.toList()
    settings.excludedFiles.addDescriptor(GlobPatternDescriptor("*.md"))
    try {
      block.invoke()
    }
    finally {
      settings.excludedFiles.apply {
        clear()
        old.forEach(this::addDescriptor)
      }
    }
  }

  private fun doTest(content: String, expected: String, count: Int = 1, string: String = " ") {
    configureFromFileText("some.md", content)
    type(string.repeat(count))
    checkResultByText(expected)
  }
}
