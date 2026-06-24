// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MarkdownHighlightingAnnotatorTest : BasePlatformTestCase() {
  fun testHeadersKeepLevelHighlighting() {
    val text = """
      # Strong **Header**

      Setext Header
      ===
    """.trimIndent()
    myFixture.configureByText("test.md", text)
    val highlights = myFixture.doHighlighting()

    assertElementHighlightedWithKey(highlights, "Strong", MarkdownHighlighterColors.HEADER_LEVEL_1)
    assertElementHighlightedWithKey(highlights, "Strong", MarkdownHighlighterColors.HEADER_LEVEL_1)
    assertElementHighlightedWithKey(highlights, "Header", MarkdownHighlighterColors.HEADER_LEVEL_1)
    assertElementHighlightedWithKey(highlights, "Setext Header", MarkdownHighlighterColors.HEADER_LEVEL_1)
  }

  fun testContainerMarkersKeepMarkerHighlighting() {
    val text = """
      # Heading Level 1
      ## Heading Level 2

      Setext Header
      ===

      > Blockquote
    """.trimIndent()
    myFixture.configureByText("test.md", text)
    val highlights = myFixture.doHighlighting()

    assertElementHighlightedWithKey(highlights, "#", MarkdownHighlighterColors.HEADER_MARKER)
    assertElementHighlightedWithKey(highlights, "#", MarkdownHighlighterColors.HEADER_LEVEL_1, HighlightingState.NOT_HIGHLIGHTED)
    assertElementHighlightedWithKey(highlights, "##", MarkdownHighlighterColors.HEADER_MARKER)
    assertElementHighlightedWithKey(highlights, "##", MarkdownHighlighterColors.HEADER_LEVEL_2, HighlightingState.NOT_HIGHLIGHTED)
    assertElementHighlightedWithKey(highlights, "===", MarkdownHighlighterColors.HEADER_MARKER)
    assertElementHighlightedWithKey(highlights, "===", MarkdownHighlighterColors.HEADER_LEVEL_1, HighlightingState.NOT_HIGHLIGHTED)
    assertElementHighlightedWithKey(highlights, ">", MarkdownHighlighterColors.BLOCK_QUOTE_MARKER)
    assertElementHighlightedWithKey(highlights, ">", MarkdownHighlighterColors.BLOCK_QUOTE, HighlightingState.NOT_HIGHLIGHTED)
  }

  fun testInlineFormattingAndLinksKeepInheritedHighlighting() {
    val text = "A **bold** and *italic* [link](target.md)"
    myFixture.configureByText("test.md", text)
    val highlights = myFixture.doHighlighting()

    assertElementHighlightedWithKey(highlights, "bold", MarkdownHighlighterColors.BOLD)
    assertElementHighlightedWithKey(highlights, "italic", MarkdownHighlighterColors.ITALIC)
    assertElementHighlightedWithKey(highlights, "link", MarkdownHighlighterColors.LINK_TEXT)
    assertElementHighlightedWithKey(highlights, "target.md", MarkdownHighlighterColors.LINK_DESTINATION)
  }

  fun testNestedContainersCreateUniquePerRangeTextAnnotations() {
    myFixture.configureByText("test.md", """
      # Header with **bold**
      # **Header** with **bold**
      > > Content
      > Content

      See[^note]
      [^note]: body text

      > [!NOTE]
    """.trimIndent())
    val duplicates = myFixture.doHighlighting()
      .filter { it.forcedTextAttributesKey != null }
      .groupBy { HighlightKey(it.startOffset, it.endOffset, it.forcedTextAttributesKey) }
      .filterValues { it.size > 1 }

    assertTrue("Duplicate forced text attribute annotations: ${duplicates.keys.joinToString()}", duplicates.isEmpty())
  }

  private fun assertElementHighlightedWithKey(
    highlights: List<HighlightInfo>,
    element: String,
    attributesKey: TextAttributesKey,
    highlightingState: HighlightingState = HighlightingState.HIGHLIGHTED
  ) {
    val fileText = myFixture.file.text
    val startOffset = fileText.indexOf(element)
    assertTrue("Fragment '$element' was not found", startOffset >= 0)
    val endOffset = startOffset + element.length
    val assertionPredicate = { highlight: HighlightInfo ->
      highlight.forcedTextAttributesKey == attributesKey && highlight.startOffset <= startOffset && highlight.endOffset >= endOffset
    }

    when (highlightingState) {
      HighlightingState.HIGHLIGHTED -> assertTrue(
        "Expected '$element' to be highlighted with $attributesKey",
        highlights.any(assertionPredicate)
      )
      HighlightingState.NOT_HIGHLIGHTED -> assertFalse(
        "Expected '$element' to be highlighted with $attributesKey",
        highlights.any(assertionPredicate)
      )
    }
  }

  private enum class HighlightingState {
    HIGHLIGHTED,
    NOT_HIGHLIGHTED
  }

  private data class HighlightKey(val startOffset: Int, val endOffset: Int, val attributesKey: TextAttributesKey?)
}
