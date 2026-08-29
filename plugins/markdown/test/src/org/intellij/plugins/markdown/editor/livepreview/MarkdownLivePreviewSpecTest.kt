// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MarkdownLivePreviewSpecTest : BasePlatformTestCase() {

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

  fun testStandaloneLocalImageConcealsItsCompleteLine() {
    val content = "  ![alt text](images/img.png)  "
    val image = elements(content).single() as MarkdownLivePreviewSpec.Image

    assertEquals(content, content.substring(image.range.startOffset, image.range.endOffset))
    assertEquals("images/img.png", image.destination)
  }

  fun testImagesBetweenHeadersAreBothSpecs() {
    val content = "# Markdown WYSIWYG Demo\n\n![logo](image.png)\n\n![logo](image.png)\n\n# Markdown WYSIWYG Demo"

    assertEquals(2, images(content).size)
  }

  fun testAdjacentImagesAreBothSpecs() {
    assertEquals(2, images("![logo](test.png)\n![logo](test.png)").size)
  }

  fun testInlineAndNestedImagesAreNotConcealed() {
    assertEmpty(images("Text ![alt](image.png) here"))
    assertEmpty(images("before\n![alt](image.png)\nafter"))
    assertEmpty(images("- ![alt](image.png)"))
    assertEmpty(images("> ![alt](image.png)"))
  }

  fun testUnsupportedImageDestinationsAreNotConcealed() {
    assertEmpty(elements("![alt](https://example.org/image.png)"))
    assertEmpty(elements("![alt](//example.org/image.png)"))
    assertEmpty(elements("![alt](data:image/png;base64,AAAA)"))
    assertEmpty(elements("![alt][image]\n\n[image]: image.png"))
  }

  fun testFileUriIsALocalImageDestination() {
    val image = images("![alt](file:///project/image.png)").single()

    assertEquals("file:///project/image.png", image.destination)
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

  fun testFrontMatterDelimitersAndThematicBreaksAreConcealed() {
    val content = """
      |---
      |name: valid-skill
      |description: Does useful work.
      |---
      |
      |---
      |***
      |___
      |tail
    """.trimMargin()
    val breaks = elements(content).filterIsInstance<MarkdownLivePreviewSpec.HorizontalRule>()

    assertEquals(
      listOf("---", "---", "---", "***", "___"),
      breaks.map { content.substring(it.range.startOffset, it.range.endOffset) },
    )
  }

  private fun MarkdownLivePreviewSpec.concealedRanges(): List<TextRange> = when (this) {
    is MarkdownLivePreviewSpec.Conceal -> conceals
    is MarkdownLivePreviewSpec.HorizontalRule -> listOf(range)
    is MarkdownLivePreviewSpec.Image -> listOf(range)
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

  private fun images(content: String): List<MarkdownLivePreviewSpec.Image> =
    elements(content).filterIsInstance<MarkdownLivePreviewSpec.Image>()
}
