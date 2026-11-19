// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.breadcrumbs

import com.intellij.codeInsight.breadcrumbs.FileBreadcrumbsCollector
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.TextRange
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.components.breadcrumbs.Crumb
import com.intellij.ui.components.breadcrumbs.StickyLineInfo
import org.assertj.core.api.Assertions.assertThat
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownBreadcrumbsProviderTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test breadcrumbs follow heading hierarchy`() {
    myFixture.configureByText(
      "test.md",
      """
        # Heading
        ## Subheading
        ### Heading 3-1
        Text<caret>
        ### Heading 3-2
      """.trimIndent()
    )

    assertEquals(
      listOf("Heading", "Subheading", "Heading 3-1"),
      timeoutRunBlocking { readAction { myFixture.getBreadcrumbsAtCaret() } }.map(Crumb::getText),
    )
  }

  @Test
  fun `test breadcrumbs are empty before the first heading`() {
    myFixture.configureByText("test.md", "Text<caret>\n# Heading")

    assertThat(timeoutRunBlocking { readAction { myFixture.getBreadcrumbsAtCaret() } }).isEmpty()
  }

  @Test
  fun `test image is hidden from breadcrumb text`() {
    myFixture.configureByText("test.md", "# ![Icon](icon.png) Heading\nText<caret>")

    assertEquals(listOf("Heading"), timeoutRunBlocking { readAction { myFixture.getBreadcrumbsAtCaret() } }.map(Crumb::getText))
  }

  @Test
  fun `test html tags are hidden from breadcrumb text`() {
    myFixture.configureByText("test.md", "# Some <b>bold</b> text\n## Foo<br>Bar\nText<caret>")

    assertEquals(listOf("Some bold text", "FooBar"), timeoutRunBlocking { readAction { myFixture.getBreadcrumbsAtCaret() } }.map(Crumb::getText))
  }

  @Test
  fun `test setext heading is included in breadcrumbs`() {
    myFixture.configureByText("test.md", "Heading\n=======\nText<caret>")

    assertEquals(listOf("Heading"), timeoutRunBlocking { readAction { myFixture.getBreadcrumbsAtCaret() } }.map(Crumb::getText))
  }

  @Test
  fun `test skipped heading level is included in breadcrumbs`() {
    myFixture.configureByText("test.md", "# Heading\n### Subheading\nText<caret>")

    assertEquals(listOf("Heading", "Subheading"), timeoutRunBlocking { readAction { myFixture.getBreadcrumbsAtCaret() } }.map(Crumb::getText))
  }

  @Test
  fun `test consecutive headings have the same parent`() {
    myFixture.configureByText("test.md", "# Heading\n## First\nText<caret>\n## Second")

    assertEquals(listOf("Heading", "First"), timeoutRunBlocking { readAction { myFixture.getBreadcrumbsAtCaret() } }.map(Crumb::getText))
  }

  @Test
  fun `test nested heading keeps outer heading`() {
    myFixture.configureByText("test.md", "# Heading\n> ## Quoted\n> Text<caret>")

    assertEquals(listOf("Heading", "Quoted"), timeoutRunBlocking { readAction { myFixture.getBreadcrumbsAtCaret() } }.map(Crumb::getText))
  }

  @Test
  fun `test heading in previous list item is included in breadcrumbs`() {
    myFixture.configureByText("test.md", "- # Heading\n- Text<caret>")

    assertEquals(listOf("Heading"), timeoutRunBlocking { readAction { myFixture.getBreadcrumbsAtCaret() } }.map(Crumb::getText))
  }

  @Test
  fun `test outer heading is not hidden by nested heading in previous list item`() {
    myFixture.configureByText("test.md", "- # A\n  ### B\n- ### C\n  Text<caret>")

    assertEquals(listOf("A", "C"), timeoutRunBlocking { readAction { myFixture.getBreadcrumbsAtCaret() } }.map(Crumb::getText))
  }

  @Test
  fun `test markdown headers are not accepted as sticky elements`() {
    myFixture.configureByText("test.md", "# Heading\nText<caret>")

    val provider = MarkdownBreadcrumbsProvider()
    val accepted = timeoutRunBlocking {
      readAction {
        val header = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownHeader::class.java) ?: error("No Markdown header")
        provider.acceptStickyElement(header)
      }
    }
    assertFalse(accepted)
  }

  @Test
  fun `test each heading is pinned until the last line of its section`() {
    val text = "# A\n## B\ntext B\n## C\ntext C"

    assertEquals(listOf("# A" to "text C", "## B" to "text B", "## C" to "text C"), stickyLines(text))
  }

  @Test
  fun `test sticky lines at a line are the heading hierarchy without previous siblings`() {
    val text = """
      # A
      ## B1
      text B1
      ## B2
      ### C1
      text C1
      ### C2
      text C2<caret>
    """.trimIndent()

    assertEquals(
      listOf("# A" to "text C2", "## B2" to "text C2", "### C2" to "text C2"),
      stickyLinesAtCaret(text),
    )
  }

  @Test
  fun `test setext heading is pinned by its first line`() {
    assertEquals(listOf("Title" to "text"), stickyLines("Title\n=====\ntext"))
  }

  @Test
  fun `test last section ends before the trailing line break`() {
    assertEquals(listOf("# A" to "text"), stickyLines("# A\ntext\n"))
  }

  @Test
  fun `test heading in a list item owns the rest of the file like the breadcrumbs`() {
    assertEquals(listOf("# A" to "- item"), stickyLines("- # A\n  text\n- item"))
  }

  @Test
  fun `test no sticky lines without headings`() {
    assertThat(stickyLines("text\nmore text")).isEmpty()
  }

  /** The sticky lines of the whole file. The platform asks the collector once for each line. */
  private fun stickyLines(text: String): List<Pair<String, String>> {
    myFixture.configureByText("test.md", text)
    return timeoutRunBlocking {
      readAction {
        val document = myFixture.editor.document
        (0 until document.lineCount).flatMap { stickyLinesAtLine(it) }.distinct().toPinnedAndScopeEnd()
      }
    }
  }

  /** The sticky lines that stay pinned while the caret line is on screen. */
  private fun stickyLinesAtCaret(text: String): List<Pair<String, String>> {
    myFixture.configureByText("test.md", text)
    return timeoutRunBlocking {
      readAction { stickyLinesAtLine(myFixture.editor.document.getLineNumber(myFixture.caretOffset)).toPinnedAndScopeEnd() }
    }
  }

  private fun stickyLinesAtLine(line: Int): List<StickyLineInfo> {
    val file = myFixture.file.virtualFile
    val document = myFixture.editor.document
    val collector = FileBreadcrumbsCollector.findBreadcrumbsCollector(myFixture.project, file)
    return collector.computeStickyLineInfos(file, document, document.getLineEndOffset(line))
  }

  /** Pairs the text of the pinned line with the text of the last line of the scope. */
  private fun List<StickyLineInfo>.toPinnedAndScopeEnd(): List<Pair<String, String>> {
    val document = myFixture.editor.document
    return sortedBy { it.textOffset }.map { info ->
      val pinnedLine = document.getLineNumber(info.textOffset)
      val scopeEndLine = document.getLineNumber(info.endOffset)
      document.getText(TextRange(info.textOffset, document.getLineEndOffset(pinnedLine))) to
        document.getText(TextRange(document.getLineStartOffset(scopeEndLine), document.getLineEndOffset(scopeEndLine)))
    }
  }
}
