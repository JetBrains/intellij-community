// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.application.options.CodeStyle
import com.intellij.application.options.codeStyle.excludedFiles.GlobPatternDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.backspace
import org.intellij.plugins.markdown.editor.executeAction
import org.intellij.plugins.markdown.editor.typeInEditor
import org.intellij.plugins.markdown.lang.formatter.settings.TableStyle
import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.datatransfer.StringSelection

@TestApplication
@Suppress("MarkdownIncorrectTableFormatting")
class MarkdownTableTypingTest {
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
    withTableStyle(myFixture.project, TableStyle.COMPACT) {
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
  fun `test backspacing last content in compact empty cell`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      withTableStyle(myFixture.project, TableStyle.COMPACT) {
        myFixture.configureByText(
          "some.md",
          """
          | a |
          | --- |
          | x<caret> |
          """.trimIndent()
        )
        myFixture.backspace()
        myFixture.checkResult(
          """
          | a |
          | --- |
          | <caret>|
          """.trimIndent()
        )
      }
    }
  }

  @Test
  fun `test typing in tight table`() {
    withTableStyle(myFixture.project, TableStyle.TIGHT) {
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
  fun `test no column expand`(): Unit = timeoutRunBlocking {
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
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      runWithDisabledFormatting(myFixture.file) {
        myFixture.typeInEditor("some")
        myFixture.checkResult(expected)
      }
    }
  }

  @Test
  fun `test no column shrink`(): Unit = timeoutRunBlocking {
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
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      runWithDisabledFormatting(myFixture.file) {
        myFixture.backspace()
        myFixture.checkResult(expected)
      }
    }
  }

  @Test
  fun `test no alignment correction`(): Unit = timeoutRunBlocking {
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
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      runWithDisabledFormatting(myFixture.file) {
        myFixture.typeInEditor("some")
        myFixture.checkResult(expected)
      }
    }
  }

  @Test
  fun `test no column expand when reformat on type is disabled`(): Unit = timeoutRunBlocking {
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
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      runWithReformatOnTypeDisabled {
        myFixture.typeInEditor("some")
        myFixture.checkResult(expected)
      }
    }
  }

  @Test
  fun `test no column shrink when reformat on type is disabled`(): Unit = timeoutRunBlocking {
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
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      runWithReformatOnTypeDisabled {
        myFixture.backspace()
        myFixture.checkResult(expected)
      }
    }
  }

  @Test
  fun `test no alignment correction when reformat on type is disabled`(): Unit = timeoutRunBlocking {
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
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      runWithReformatOnTypeDisabled {
        myFixture.typeInEditor("some")
        myFixture.checkResult(expected)
      }
    }
  }

  @Test
  fun `test pasting reformats the table`(): Unit = timeoutRunBlocking {
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
    myFixture.configureByText("some.md", before)
    withContext(Dispatchers.EDT) {
      CopyPasteManager.getInstance().setContents(StringSelection("xyz"))
      myFixture.executeAction(IdeActions.ACTION_EDITOR_PASTE)
      myFixture.checkResult(after)
    }
  }

  @Test
  fun `test cutting reformats the table`(): Unit = timeoutRunBlocking {
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
    myFixture.configureByText("some.md", before)
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_CUT)
      myFixture.checkResult(after)
    }
  }

  @Test
  fun `test duplicating reformats the table`(): Unit = timeoutRunBlocking {
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
    myFixture.configureByText("some.md", before)
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_DUPLICATE)
      myFixture.checkResult(after)
    }
  }

  @Test
  fun `test deleting to word start reformats the table`(): Unit = timeoutRunBlocking {
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
    myFixture.configureByText("some.md", before)
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_START)
      myFixture.checkResult(after)
    }
  }

  @Test
  fun `test no reformat on pasting when reformat on type is disabled`(): Unit = timeoutRunBlocking {
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
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      CopyPasteManager.getInstance().setContents(StringSelection("xyz"))
      runWithReformatOnTypeDisabled {
        myFixture.executeAction(IdeActions.ACTION_EDITOR_PASTE)
        myFixture.checkResult(expected)
      }
    }
  }

  @Test
  fun `test pasting outside of a table keeps the text`(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "Some text<caret>")
    withContext(Dispatchers.EDT) {
      CopyPasteManager.getInstance().setContents(StringSelection("xyz"))
      myFixture.executeAction(IdeActions.ACTION_EDITOR_PASTE)
      myFixture.checkResult("Some textxyz")
    }
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

  private fun doTest(content: String, expected: String, count: Int = 1, string: String = " "): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor(string.repeat(count))
      myFixture.checkResult(expected)
    }
  }
}
