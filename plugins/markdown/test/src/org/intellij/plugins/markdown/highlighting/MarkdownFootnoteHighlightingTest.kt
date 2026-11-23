// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.highlighting

import com.intellij.openapi.application.readAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownFootnoteHighlightingTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun testConsecutiveFootnoteReferencesHighlighting() {
    myFixture.configureByText("test.md", """
      See[^note1][^note2]

      [^note1]: First.

      [^note2]: Second.
    """.trimIndent())
    val text = fileText()
    val highlights = myFixture.doHighlighting()
    // First occurrences are in "See[^note1][^note2]" (the references)
    val note1Offset = text.indexOf("[^note1]")
    val note2Offset = text.indexOf("[^note2]")
    // Each reference should be highlighted with LINK_LABEL color, same as the label inside a standalone [^note]
    assertTrue(highlights.any {
        it.hasTextAttributesKey(MarkdownHighlighterColors.LINK_LABEL)
        && it.startOffset == note1Offset && it.endOffset == note1Offset + 8
      },
      "[^note1] not highlighted as link label")
    assertTrue(highlights.any {
        it.hasTextAttributesKey(MarkdownHighlighterColors.LINK_LABEL)
        && it.startOffset == note2Offset && it.endOffset == note2Offset + 8
      },
      "[^note2] not highlighted as link label")
    // [^note1] must not appear as LINK_TEXT (hyperlink blue)
    assertFalse(highlights.any {
        it.forcedTextAttributesKey == MarkdownHighlighterColors.LINK_TEXT
        && it.startOffset == note1Offset && it.endOffset == note1Offset + 8
      },
      "[^note1] incorrectly highlighted as link text")
  }

  @Test
  fun testLinkDefinitionFootnoteHighlighting() {
    myFixture.configureByText("test.md", """
      See[^note]

      [^note]: body text
    """.trimIndent())
    val text = fileText()
    val ranges = myFixture.doHighlighting()
      .filter { it.hasTextAttributesKey(MarkdownHighlighterColors.FOOTNOTE_DEFINITION) }
      .map { text.substring(it.startOffset, it.endOffset) }
    assertTrue(ranges.any { it.contains("body text") })
  }

  @Test
  fun testFootnoteReferencesInContinuationCodeLines() {
    myFixture.configureByText("test.md", """
      See[^note1]

      [^note1]: First paragraph.

          See[^note2]

          [^note2]: body
    """.trimIndent())
    val text = fileText()
    val highlights = myFixture.doHighlighting()
    // "    See[^note2]" is a CODE_LINE in a footnote continuation block;
    // [^note2] within it must be overlaid with LINK_LABEL color
    val lineStart = text.indexOf("    See[^note2]")
    val refStart = lineStart + "    See".length
    assertTrue(highlights.any {
        it.hasTextAttributesKey(MarkdownHighlighterColors.LINK_LABEL)
        && it.startOffset == refStart && it.endOffset == refStart + 8
      },
      "[^note2] inside continuation CODE_LINE should be highlighted as LINK_LABEL")
    assertTrue(highlights.any {
        it.hasTextAttributesKey(MarkdownHighlighterColors.BOLD)
        && it.startOffset == refStart && it.endOffset == refStart + 8
      },
      "[^note2] inside continuation CODE_LINE should be highlighted as BOLD")
  }

  @Test
  fun testMultilineFootnoteHighlighting() {
    myFixture.configureByText("test.md", """
      See[^note]
      
      [^note]: First paragraph.
      
          Line one.
          Line two.
    """.trimIndent())
    val text = fileText()
    val ranges = myFixture.doHighlighting()
      .filter { it.hasTextAttributesKey(MarkdownHighlighterColors.FOOTNOTE_DEFINITION) }
      .map { text.substring(it.startOffset, it.endOffset) }
    assertTrue(ranges.any { it.contains("First paragraph.") })
    assertTrue(ranges.any { it.contains("Line one.") })
    assertTrue(ranges.any { it.contains("Line two.") })
  }

  @Test
  fun testFootnoteLabelWithSpaceNotHighlighted() {
    myFixture.configureByText("test.md", """
      [^my note]: body text
    """.trimIndent())
    val ranges = myFixture.doHighlighting()
      .filter { it.forcedTextAttributesKey == MarkdownHighlighterColors.FOOTNOTE_DEFINITION }
    assertTrue(ranges.isEmpty(), "Label with space must not be highlighted as footnote definition")
  }

  private fun fileText(): String = timeoutRunBlocking { readAction { myFixture.file.text } }
}
