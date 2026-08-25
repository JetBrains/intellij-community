// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MarkdownConcealSpecTest: BasePlatformTestCase() {

  fun testEmphasisMarkersAreConcealed() {
    val content = "Some **bold**, *italic* and ~~gone~~ text"
    assertEquals(listOf("**", "**", "*", "*", "~~", "~~"), concealed(content))
    assertEquals(listOf("**bold**", "*italic*", "~~gone~~"), revealRanges(content))
  }

  fun testUnderscoreEmphasisIsConcealed() {
    assertEquals(listOf("__", "__", "_", "_"), concealed("__bold__ and _italic_"))
  }

  fun testCodeSpanAndInlineLinkAreConcealed() {
    val content = "Call `foo()` or read [docs](https://example.org)"
    assertEquals(listOf("`", "`", "[", "](https://example.org)"), concealed(content))
    assertEquals(listOf("`foo()`", "[docs](https://example.org)"), revealRanges(content))
  }

  fun testWrappedAutolinksAreConcealed() {
    assertEquals(listOf("<", ">"), concealed("Read <https://example.org>"))
    assertEquals(listOf("<", ">"), concealed("Mail <team@example.org>"))
  }

  fun testBareAutolinkIsNotConcealed() {
    assertEmpty(concealed("Read https://example.org"))
  }

  fun testEmphasisInsideCodeSpanIsNotConcealed() {
    assertEquals(listOf("`", "`"), concealed("`**not bold**`"))
  }

  fun testEmphasisInsideLinkTextIsConcealed() {
    val content = "[**bold** title](https://example.org)"
    assertEquals(listOf("[", "](https://example.org)", "**", "**"), concealed(content))
  }

  fun testImageSyntaxIsNotConcealed() {
    assertEmpty(concealed("![alt text](images/img.png)"))
  }

  fun testReferenceLinksAreNotConcealed() {
    assertEmpty(concealed("[text][label] and [label]\n\n[label]: https://example.org"))
  }

  fun testCodeFenceContentIsNotConcealed() {
    val content = """
      |```markdown
      |**bold** and [docs](https://example.org)
      |```
    """.trimMargin()
    assertEmpty(concealed(content))
  }

  fun testIndentedCodeBlockContentIsNotConcealed() {
    assertEmpty(concealed("text\n\n    **bold**\n"))
  }

  fun testUnbalancedMarkersAreNotConcealed() {
    assertEmpty(concealed("**not bold and `not code"))
  }

  fun testEmptyEmphasisIsNotConcealed() {
    assertEmpty(concealed("**** and ``"))
  }

  fun testNestedEmphasisRevealRangesAreNested() {
    val content = "**bold *and italic* here**"
    val elements = elements(content)
    assertEquals(2, elements.size)
    val outer = elements[0]
    val inner = elements[1]
    assertEquals("**bold *and italic* here**", content.substring(outer.range.startOffset, outer.range.endOffset))
    assertEquals("*and italic*", content.substring(inner.range.startOffset, inner.range.endOffset))
    assertTrue("The inner element must be contained in the outer one", outer.range.contains(inner.range))
  }

  fun testHeaderInlineMarkersAreConcealed() {
    assertEquals(listOf("*", "*"), concealed("### Deep *header*"))
  }

  fun testUnorderedListBulletsUseDepthPlaceholders() {
    val content = """
      |- one
      |  * two
      |    + three
      |      - four
    """.trimMargin()
    val elements = elements(content)
    assertEquals(listOf("-", "*", "+", "-"), concealed(content))
    assertEquals(listOf("- ", "* ", "+ ", "- "), revealRanges(content))
    assertEquals(listOf("•", "◦", "▪", "•"), elements.map { (it as MarkdownLivePreviewSpec.Bullet).placeholderText })
  }

  fun testOrderedListParentsCountTowardsBulletDepth() {
    val content = "1. one\n   - two"
    val elements = elements(content)
    assertEquals(listOf("-"), concealed(content))
    assertEquals(listOf("◦"), elements.map { (it as MarkdownLivePreviewSpec.Bullet).placeholderText })
  }

  fun testOrderedAndTaskListMarkersAreNotConcealed() {
    assertEmpty(elements("1. ordered\n- [ ] todo\n- [x] done"))
  }

  fun testTableCellInlineMarkersAreNotConcealed() {
    val content = """
      || Name |
      || --- |
      || **bold** |
    """.trimMargin()
    assertEmpty(concealed(content))
  }

  fun testThematicBreaksConcealTheirCompleteLines() {
    val content = "---\n***\n___\n  *  *  *  \ntail"
    val breaks = elements(content).filterIsInstance<MarkdownLivePreviewSpec.HorizontalRule>()

    assertEquals(4, breaks.size)
    assertEquals(listOf("---", "***", "___", "  *  *  *  "), breaks.map { content.substring(it.range.startOffset, it.range.endOffset) })
    assertTrue(breaks.all { it.range.length > 0 })
  }

  private fun MarkdownLivePreviewSpec.concealedRanges(): List<TextRange> = when (this) {
    is MarkdownLivePreviewSpec.Conceal -> conceals
    is MarkdownLivePreviewSpec.HorizontalRule -> listOf(range)
    is MarkdownLivePreviewSpec.Bullet -> listOf(concealRange)
  }

  private fun elements(content: String): List<MarkdownLivePreviewSpec> {
    myFixture.configureByText("test.md", content)
    val elements = computeLivePreviewSpecs(myFixture.file)
    for (element in elements) {
      val conceals = element.concealedRanges()
      assertFalse("An element with nothing to conceal must not be reported: $element", conceals.isEmpty())
      assertTrue("An element must contain the markup it conceals: $element", conceals.all { element.range.contains(it) })
    }
    return elements
  }

  private fun concealed(content: String): List<String> =
    elements(content).flatMap { it.concealedRanges() }.map { content.substring(it.startOffset, it.endOffset) }

  private fun revealRanges(content: String): List<String> =
    elements(content).map { content.substring(it.range.startOffset, it.range.endOffset) }
}
