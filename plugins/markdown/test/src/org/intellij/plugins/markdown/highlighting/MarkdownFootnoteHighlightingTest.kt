// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.highlighting

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MarkdownFootnoteHighlightingTest : BasePlatformTestCase() {

  fun testConsecutiveFootnoteReferencesHighlighting() {
    myFixture.configureByText("test.md", """
      See[^note1][^note2]

      [^note1]: First.

      [^note2]: Second.
    """.trimIndent())
    val text = myFixture.file.text
    val highlights = myFixture.doHighlighting()
    // First occurrences are in "See[^note1][^note2]" (the references)
    val note1Offset = text.indexOf("[^note1]")
    val note2Offset = text.indexOf("[^note2]")
    // Each reference should be highlighted with LINK_LABEL color, same as the label inside a standalone [^note]
    assertTrue("[^note1] not highlighted as link label",
      highlights.any {
        it.forcedTextAttributesKey == MarkdownHighlighterColors.LINK_LABEL
        && it.startOffset == note1Offset && it.endOffset == note1Offset + 8
      })
    assertTrue("[^note2] not highlighted as link label",
      highlights.any {
        it.forcedTextAttributesKey == MarkdownHighlighterColors.LINK_LABEL
        && it.startOffset == note2Offset && it.endOffset == note2Offset + 8
      })
    // [^note1] must not appear as LINK_TEXT (hyperlink blue)
    assertFalse("[^note1] incorrectly highlighted as link text",
      highlights.any {
        it.forcedTextAttributesKey == MarkdownHighlighterColors.LINK_TEXT
        && it.startOffset == note1Offset && it.endOffset == note1Offset + 8
      })
  }

  fun testLinkDefinitionFootnoteHighlighting() {
    myFixture.configureByText("test.md", """
      See[^note]

      [^note]: body text
    """.trimIndent())
    val text = myFixture.file.text
    val ranges = myFixture.doHighlighting()
      .filter { it.forcedTextAttributesKey == MarkdownHighlighterColors.FOOTNOTE_DEFINITION }
      .map { text.substring(it.startOffset, it.endOffset) }
    assertTrue(ranges.any { it.contains("body text") })
  }

  fun testFootnoteReferencesInContinuationCodeLines() {
    myFixture.configureByText("test.md", """
      See[^note1]

      [^note1]: First paragraph.

          See[^note2]

          [^note2]: body
    """.trimIndent())
    val text = myFixture.file.text
    val highlights = myFixture.doHighlighting()
    // "    See[^note2]" is a CODE_LINE in a footnote continuation block;
    // [^note2] within it must be overlaid with LINK_LABEL color
    val lineStart = text.indexOf("    See[^note2]")
    val refStart = lineStart + "    See".length
    assertTrue("[^note2] inside continuation CODE_LINE should be highlighted as LINK_LABEL",
      highlights.any {
        it.forcedTextAttributesKey == MarkdownHighlighterColors.LINK_LABEL
        && it.startOffset == refStart && it.endOffset == refStart + 8
      })
    assertTrue("[^note2] inside continuation CODE_LINE should be highlighted as BOLD",
      highlights.any {
        it.forcedTextAttributesKey == MarkdownHighlighterColors.BOLD
        && it.startOffset == refStart && it.endOffset == refStart + 8
      })
  }

  fun testMultilineFootnoteHighlighting() {
    myFixture.configureByText("test.md", """
      See[^note]
      
      [^note]: First paragraph.
      
          Line one.
          Line two.
    """.trimIndent())
    val text = myFixture.file.text
    val ranges = myFixture.doHighlighting()
      .filter { it.forcedTextAttributesKey == MarkdownHighlighterColors.FOOTNOTE_DEFINITION }
      .map { text.substring(it.startOffset, it.endOffset) }
    assertTrue(ranges.any { it.contains("First paragraph.") })
    assertTrue(ranges.any { it.contains("Line one.") })
    assertTrue(ranges.any { it.contains("Line two.") })
  }

  fun testFootnoteLabelWithSpaceNotHighlighted() {
    myFixture.configureByText("test.md", """
      [^my note]: body text
    """.trimIndent())
    val ranges = myFixture.doHighlighting()
      .filter { it.forcedTextAttributesKey == MarkdownHighlighterColors.FOOTNOTE_DEFINITION }
    assertTrue("Label with space must not be highlighted as footnote definition", ranges.isEmpty())
  }
}
