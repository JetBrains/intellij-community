// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import java.awt.datatransfer.StringSelection
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.readText

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/editor/paste")
internal class MarkdownIndentPastePreProcessorTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test paste preserves footnote indentation`() {
    val pasted = Path(myFixture.testDataPath, "footnoteIndentation.md").readText()
    doTest("<caret>", pasted, "$pasted<caret>")
  }

  @Test
  fun `test paste preserves footnote continuation indentation`() {
    val pasted = "[^note]:\n\tFootnote text"
    doTest("<caret>", pasted, "$pasted<caret>")
  }

  @Test
  fun `test paste adjusts prose indentation inside a list`() {
    doTest("- <caret>", "first\n second", "- first\n  second<caret>")
  }

  @Test
  fun `test paste into quote in list`() {
    doTest(
      """
      * item
        * > <caret>
        * item 2
      """,
      """
      a multiline
      string to paste
      within list
      """.trimIndent(),
      """
      * item
        * > a multiline
          > string to paste
          > within list<caret>
        * item 2
      """,
    )
  }

  @Test
  fun `test paste into quote`() {
    doTest(
      """
      > quote
      > <caret>
      """,
      "a\nb",
      """
      > quote
      > a
      > b<caret>
      """,
    )
  }

  @Test
  fun `test paste into nested quote`() {
    doTest(
      """
      > > <caret>
      """,
      "a\nb",
      """
      > > a
      > > b<caret>
      """,
    )
  }

  @Test
  fun `test paste of quote lines into quote`() {
    doTest(
      """
      > quote
      > <caret>
      """,
      "a\n> b",
      """
      > quote
      > a
      > b<caret>
      """,
    )
  }

  @Test
  fun `test paste into fence in list`() {
    doTest(
      """
      * item
        * ```
          <caret>
          ```
      """,
      """
      a multiline
      string to paste
      within list
      """.trimIndent(),
      """
      * item
        * ```
          a multiline
          string to paste
          within list<caret>
          ```
      """,
    )
  }

  @Test
  fun `test paste into fence in quote`() {
    doTest(
      """
      > ```
      > <caret>
      > ```
      """,
      "a\nb",
      """
      > ```
      > a
      > b<caret>
      > ```
      """,
    )
  }

  @Test
  fun `test paste into fence`() {
    doTest(
      """
      ```
      <caret>
      ```
      """,
      "a\n  b",
      """
      ```
      a
        b<caret>
      ```
      """,
    )
  }

  @Test
  fun `test paste of whole lines at line start in fence in list`() {
    doTest(
      """
      * item
        * ```
      <caret>    x
          ```
      """,
      "    a\n    b\n",
      """
      * item
        * ```
          a
          b
      <caret>    x
          ```
      """,
    )
  }

  private fun doTest(before: String, pasted: String, after: String) {
    myFixture.configureByText("test.md", before.trimIndent())
    CopyPasteManager.getInstance().setContents(StringSelection(pasted))
    myFixture.performEditorAction(IdeActions.ACTION_PASTE)
    myFixture.checkResult(after.trimIndent())
  }
}
