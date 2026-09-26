// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.ide.rpc.DocumentPatchVersion
import com.intellij.markdown.backend.editor.livepreview.computeLivePreviewSpecs
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.MarkdownBundle
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

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
    assertEquals("alt text", image.placeholderText)
  }

  fun testImagePlaceholderIsTheAltTextOnOneLine() {
    assertEquals("alt text", images("![alt text](image.png)").single().placeholderText)
    assertEquals("first second", images("![  first\n  second  ](image.png)").single().placeholderText)
  }

  fun testImageWithoutAltTextUsesTheGenericPlaceholder() {
    val placeholder = MarkdownBundle.message("markdown.live.preview.image.placeholder")

    assertEquals(placeholder, images("![](image.png)").single().placeholderText)
    assertEquals(placeholder, images("![   ](image.png)").single().placeholderText)
  }

  fun testImagesBetweenHeadersAreBothSpecs() {
    val content = "# Markdown WYSIWYG Demo\n\n![logo](image.png)\n\n![logo](image.png)\n\n# Markdown WYSIWYG Demo"

    assertEquals(2, images(content).size)
  }

  fun testAdjacentImagesAreBothSpecs() {
    assertEquals(2, images("![logo](test.png)\n![logo](test.png)").size)
  }

  fun testInlineAndNestedImagesConcealTheirElement() {
    assertEquals(listOf("![alt](image.png)"), imageRanges("Text ![alt](image.png) here"))
    assertEquals(listOf("![alt](image.png)"), imageRanges("- ![alt](image.png)"))
    assertEquals(listOf("![alt](image.png)"), imageRanges("> ![alt](image.png)"))
    assertEquals(listOf("![alt](image.png)"), imageRanges("# ![alt](image.png)"))
  }

  fun testImageLineInsideParagraphConcealsItsCompleteLine() {
    assertEquals(listOf("  ![alt](image.png) "), imageRanges("before\n  ![alt](image.png) \nafter"))
  }

  fun testTwoImagesOnOneLineConcealTheirElements() {
    val content = "![one](one.png) ![two](two.png)"
    val images = images(content)

    assertEquals(listOf("one.png", "two.png"), images.map { it.destination })
    assertEquals(listOf("one", "two"), images.map { it.placeholderText })
    assertEquals(listOf("![one](one.png)", "![two](two.png)"), imageRanges(content))
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

  fun testHeaderInlineMarkersAreConcealedInsideTheHeading() {
    assertEquals(listOf("### Deep *header*", "*", "*"), concealed("### Deep *header*"))
  }

  fun testTopLevelAtxHeadingsCoverTheirLines() {
    val content = "# One\n## Two\n### Three\n#### Four\n##### Five\n###### Six"
    val headings = headings(content)

    assertEquals((1..6).toList(), headings.map { it.level })
    assertEquals(listOf("One", "Two", "Three", "Four", "Five", "Six"), headings.map { it.body().text() })
    assertEquals(content.lines(), headings.map { content.substring(it.range.startOffset, it.range.endOffset) })
  }

  fun testHeadingHtmlHidesMarkersIndentAndLinkDestinations() {
    val content = "before\n  ### [visible](https://example.org) text ###  \n\nafter"
    val heading = headings(content).single()

    assertEquals("  ### [visible](https://example.org) text ###  ", content.substring(heading.range.startOffset, heading.range.endOffset))
    assertEquals("visible text", heading.body().text())
    assertEquals("https://example.org", heading.body().select("a").attr("href"))
  }

  fun testHeadingHtmlRendersInlineStyles() {
    val body = headings("# **bold** *em* `code` ~~gone~~ [link](https://example.org)").single().body()

    assertEquals("bold", body.select("strong").text())
    assertEquals("em", body.select("em").text())
    assertEquals("code", body.select("code").text())
    assertEquals("gone", body.select(".user-del").text())
    assertEquals("link", body.select("a").text())
    assertEquals("bold em code gone link", body.text())
  }

  fun testHeadingKeepsRawHtmlAndMathAsText() {
    val content = $$"# Use <kbd>Ctrl</kbd> if $a<b$"
    val body = headings(content).single().body()

    assertEmpty(body.select("kbd"))
    val tag = body.select("span[md-src-pos]").first { it.wholeText() == "<kbd>" }
    val (start, end) = tag.attr("md-src-pos").split("..").map(String::toInt)
    assertEquals("<kbd>", content.substring(start, end))
    assertTrue(body.text(), body.text().startsWith("Use <kbd>Ctrl</kbd> if "))
    assertTrue(body.text(), body.text().contains("a<b"))
  }

  fun testHeadingDropsImagesAndKeepsTheImageSpec() {
    val content = "# ![logo](logo.png) Title"

    assertEquals("Title", headings(content).single().body().text())
    assertEmpty(headings(content).single().body().select("img"))
    assertEquals(1, images(content).size)
  }

  fun testHeadingSpansCoverTheirSource() {
    val content = "before\n# Hello **big** (world): *yes* & \\*no\\*"
    val heading = headings(content).single()
    val line = content.substring(heading.range.startOffset, heading.range.endOffset)
    val spans = heading.body().select("span[md-src-pos]")

    assertEquals("Hello big (world): yes & *no*", spans.joinToString("") { it.wholeText() })
    for (span in spans.filter { it.wholeText().none { char -> char == '&' || char == '*' } }) {
      val (start, end) = span.attr("md-src-pos").split("..").map(String::toInt)
      assertEquals(span.toString(), span.wholeText(), line.substring(start, end))
    }
  }

  fun testHeadingHtmlDoesNotDependOnTheTextBeforeTheHeading() {
    assertEquals(headings("# Hello **big**").single().html, headings("some text\n\n# Hello **big**").single().html)
  }

  fun testNestedAndUnsupportedHeadingsAreNotReported() {
    val content = """
      |- # list heading
      |> ## quote heading
      |```markdown
      |### code heading
      |```
      |<section>
      |#### html heading
      |</section>
      |
      |Setext heading
      |--------------
      |
      |####### too deep
      |
      |    # indented code
      |
      |# top-level heading
    """.trimMargin()
    val heading = headings(content).single()

    assertEquals(1, heading.level)
    assertEquals("# top-level heading", content.substring(heading.range.startOffset, heading.range.endOffset))
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
    assertEquals(listOf("-", "*", "+", "-"), revealRanges(content))
    assertEquals(listOf("•", "◦", "▪", "•"), elements.map { (it as MarkdownLivePreviewSpec.Bullet).placeholderText })
  }

  fun testOrderedListParentsCountTowardsBulletDepth() {
    val content = "1. one\n   - two"
    val elements = elements(content)
    assertEquals(listOf("-"), concealed(content))
    assertEquals(listOf("◦"), elements.map { (it as MarkdownLivePreviewSpec.Bullet).placeholderText })
  }

  fun testOrderedListMarkersAreNotConcealed() {
    assertEmpty(elements("1. ordered"))
  }

  fun testTaskCheckboxesConcealAndRevealTheirMarkers() {
    val content = "- [ ] todo\n* [x] done\n+ [X] done too\n1. [ ] ordered"
    val tasks = elements(content).filterIsInstance<MarkdownLivePreviewSpec.TaskCheckbox>()
    assertEquals(listOf(false, true, true, false), tasks.map { it.checked })
    assertEquals(listOf("- [ ]", "* [x]", "+ [X]", "[ ]"), concealed(content))
    assertEquals(listOf("- [ ]", "* [x]", "+ [X]", "[ ]"), revealRanges(content))
    assertEquals(listOf("[ ]", "[x]", "[X]", "[ ]"), tasks.map {
      content.substring(it.range.endOffset - 3, it.range.endOffset)
    })
  }

  fun testNestedTaskAndQuotedTaskPreserveTheirIndentationAndQuoteMarkers() {
    val content = "- [ ] parent\n  continuation\n  - [x] child\n\n> - [ ] quoted"
    assertEquals(listOf("- [ ]", "- [x]", "> ", "- [ ]"), concealed(content))
    assertEquals(listOf("- [ ]", "- [x]", "> - [ ] quoted", "- [ ]"), revealRanges(content))
  }

  fun testBlockquotesConcealMarkersAndCoverBlankLines() {
    val content = "> first\n> second\n>\n> last"
    assertEquals(listOf("> ", "> ", ">", "> "), concealed(content))
    assertEquals(listOf(content), revealRanges(content))
  }

  fun testNestedBlockquotesHaveNestedRanges() {
    val content = "> outer\n> > inner\n> outer"
    assertEquals(listOf("> ", "> ", "> ", "> "), concealed(content))
    assertEquals(listOf(content, "> > inner"), revealRanges(content))
  }

  fun testBlockquoteMarkersCanChangeIndentation() {
    val content = "> first\n  > second\n> third"
    assertEquals(listOf("> ", "> ", "> "), concealed(content))
    assertEquals(listOf(content), revealRanges(content))
  }

  fun testNestedBlockquoteRangeStopsAtTheOuterSibling() {
    val content = "> Bad example. More messages\n> > Another example\n> Test"
    assertEquals(listOf("> ", "> ", "> ", "> "), concealed(content))
    assertEquals(listOf(content, "> > Another example"), revealRanges(content))
  }

  fun testBlockquoteContinuationMarkersAreConcealed() {
    val content = "> first\n> second\n> third"
    assertEquals(listOf("> ", "> ", "> "), concealed(content))
    assertEquals(listOf(content), revealRanges(content))
  }

  fun testBlockquoteMarkersInsideListItemsAreConcealed() {
    val content = "- > first\n  > second\n  > third"
    assertEquals(listOf("-", "> ", "> ", "> "), concealed(content))
    assertEquals(listOf("-", content), revealRanges(content))
  }

  fun testBlockquoteMarkersInsidePlusListItemsAreConcealed() {
    val content = "+ > first\n  > second\n  > third"
    assertEquals(listOf("+", "> ", "> ", "> "), concealed(content))
    assertEquals(listOf("+", content), revealRanges(content))
  }

  fun testBlockquoteMarkersInsideAsteriskListItemsAreConcealed() {
    val content = "* > first\n  > second\n  > third"
    assertEquals(listOf("*", "> ", "> ", "> "), concealed(content))
    assertEquals(listOf("*", content), revealRanges(content))
  }

  fun testBlockquoteMarkersInsideOrderedListItemsAreConcealed() {
    val content = "1. > first\n   > second\n   > third"
    assertEquals(listOf("> ", "> ", "> "), concealed(content))
    assertEquals(listOf(content), revealRanges(content))
  }

  fun testBlockquoteMarkersInsideParenthesizedListItemsAreConcealed() {
    val content = "12) > first\n    > second\n    > third"
    assertEquals(listOf("> ", "> ", "> "), concealed(content))
    assertEquals(listOf(content), revealRanges(content))
  }

  fun testManySeparateBlockquotes() {
    val quotes = (1..200).map { "> quote $it" }
    val content = quotes.joinToString("\n\n")
    assertEquals(List(200) { "> " }, concealed(content))
    assertEquals(quotes, revealRanges(content))
  }

  fun testDeepBlockquotesConcealEachMarkerOnce() {
    val prefix = "> ".repeat(64)
    val quote = "${prefix}first\n${prefix}second"
    val content = "$quote\n" + "continuation\n".repeat(200)
    assertEquals(List(128) { "> " }, concealed(content))
    assertEquals(List(64) { quote }, revealRanges(content))
  }

  fun testSiblingNestedBlockquotesKeepSeparateMarkers() {
    val content = "> > first\n>\n> > second"
    assertEquals(listOf("> ", "> ", ">", "> ", "> "), concealed(content))
    assertEquals(listOf("> > first", content, "> > second"), revealRanges(content))
  }

  fun testTaskExamplesOutsideListsAreNotCheckboxes() {
    val content = "[ ] plain\n[x] plain\n\n```markdown\n- [ ] example\n```\n\n    - [x] code\n\n`- [ ] inline`"
    assertEmpty(elements(content).filterIsInstance<MarkdownLivePreviewSpec.TaskCheckbox>())
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

  fun testDocumentVersionUsesLocalStampWithoutPatchVersion() {
    myFixture.configureByText("test.md", "text")
    val document = myFixture.editor.document as DocumentEx
    val version = MarkdownLivePreviewDocumentVersion.capture(document, project)

    assertTrue(version.matches(document, project))
    document.setModificationStamp(document.modificationStamp + 1)
    assertFalse(version.matches(document, project))
  }

  fun testDocumentVersionUsesPatchVersionWhenPresent() {
    val patchVersion = DocumentPatchVersion(7, 11)

    assertTrue(MarkdownLivePreviewDocumentVersion(patchVersion, 42).matchesDocument(MarkdownLivePreviewDocumentVersion(patchVersion, 99)))
    assertFalse(
      MarkdownLivePreviewDocumentVersion(patchVersion, 42).matchesDocument(
        MarkdownLivePreviewDocumentVersion(DocumentPatchVersion(8, 11), 42)
      )
    )
    assertFalse(MarkdownLivePreviewDocumentVersion(patchVersion, 42).matchesDocument(MarkdownLivePreviewDocumentVersion(null, 42)))
  }

  private fun MarkdownLivePreviewSpec.concealedRanges(): List<MarkdownLivePreviewRange> = when (this) {
    is MarkdownLivePreviewSpec.Conceal -> conceals
    is MarkdownLivePreviewSpec.BlockQuote -> markerRanges
    is MarkdownLivePreviewSpec.HorizontalRule -> listOf(range)
    is MarkdownLivePreviewSpec.Heading -> listOf(range)
    is MarkdownLivePreviewSpec.Image -> listOf(range)
    is MarkdownLivePreviewSpec.Bullet -> listOf(range)
    is MarkdownLivePreviewSpec.TaskCheckbox -> listOf(range)
  }

  private fun elements(content: String): List<MarkdownLivePreviewSpec> {
    myFixture.configureByText("test.md", content)
    val elements = computeLivePreviewSpecs(myFixture.file, myFixture.editor).elements
    for (element in elements) {
      val conceals = element.concealedRanges()
      assertFalse("An element with nothing to conceal must not be reported: $element", conceals.isEmpty())
      assertTrue("An element must contain the markup it conceals: $element", conceals.all { element.range.contains(it) })
    }
    val ranges = elements.flatMap { it.concealedRanges() }
    assertEquals("Each range must be concealed only once", ranges.distinct(), ranges)
    return elements
  }

  private fun concealed(content: String): List<String> =
    elements(content).flatMap { it.concealedRanges() }.map { content.substring(it.startOffset, it.endOffset) }

  private fun revealRanges(content: String): List<String> =
    elements(content).map { content.substring(it.range.startOffset, it.range.endOffset) }

  private fun images(content: String): List<MarkdownLivePreviewSpec.Image> =
    elements(content).filterIsInstance<MarkdownLivePreviewSpec.Image>()

  private fun headings(content: String): List<MarkdownLivePreviewSpec.Heading> =
    elements(content).filterIsInstance<MarkdownLivePreviewSpec.Heading>()

  private fun MarkdownLivePreviewSpec.Heading.body(): Element = Jsoup.parseBodyFragment(html).body()

  private fun imageRanges(content: String): List<String> =
    images(content).map { content.substring(it.range.startOffset, it.range.endOffset) }

  private fun MarkdownLivePreviewRange.contains(other: MarkdownLivePreviewRange): Boolean {
    return startOffset <= other.startOffset && endOffset >= other.endOffset
  }

}
