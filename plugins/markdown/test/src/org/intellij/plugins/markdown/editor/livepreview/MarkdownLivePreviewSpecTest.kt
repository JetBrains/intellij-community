// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.ide.rpc.DocumentPatchVersion
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.TrustedProjectsTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec.AlertType
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownLivePreviewSpecTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun testEmphasisMarkersAreConcealed() {
    val content = "Some **bold**, *italic* and ~~gone~~ text"
    assertEquals(listOf("**", "**", "*", "*", "~~", "~~"), concealed(content))
    assertEquals(listOf("**bold**", "*italic*", "~~gone~~"), revealRanges(content))
  }

  @Test
  fun testHighlightMarkersAreConcealed() {
    val content = "Some ==marked== and **bold ==nested== text**"
    assertEquals(listOf("==", "==", "**", "**", "==", "=="), concealed(content))
    assertEquals(listOf("==marked==", "**bold ==nested== text**", "==nested=="), revealRanges(content))
  }

  @Test
  fun testSingleAndTripleEqualsAreNotConcealed() {
    assertThat(concealed("A =one= and ===three=== and ==open")).isEmpty()
  }

  @Test
  fun testUnderscoreEmphasisIsConcealed() {
    assertEquals(listOf("__", "__", "_", "_"), concealed("__bold__ and _italic_"))
  }

  @Test
  fun testCodeSpanAndInlineLinkAreConcealed() {
    val content = "Call `foo()` or read [docs](https://example.org)"
    assertEquals(listOf("`", "`", "[", "](https://example.org)"), concealed(content))
    assertEquals(listOf("`foo()`", "[docs](https://example.org)"), revealRanges(content))
  }

  @Test
  fun testWrappedAutolinksAreConcealed() {
    assertEquals(listOf("<", ">"), concealed("Read <https://example.org>"))
    assertEquals(listOf("<", ">"), concealed("Mail <team@example.org>"))
  }

  @Test
  fun testBareAutolinkIsNotConcealed() {
    assertThat(concealed("Read https://example.org")).isEmpty()
  }

  @Test
  fun testEmphasisInsideCodeSpanIsNotConcealed() {
    assertEquals(listOf("`", "`"), concealed("`**not bold**`"))
  }

  @Test
  fun testEmphasisInsideLinkTextIsConcealed() {
    val content = "[**bold** title](https://example.org)"
    assertEquals(listOf("[", "](https://example.org)", "**", "**"), concealed(content))
  }

  @Test
  fun testStandaloneLocalImageConcealsItsCompleteLine() {
    val content = "  ![alt text](images/img.png)  "
    (val range, val destination, val placeholderText) = elements(content).single() as MarkdownLivePreviewSpec.Image

    assertEquals(content, content.substring(range.startOffset, range.endOffset))
    assertEquals("images/img.png", destination)
    assertEquals("alt text", placeholderText)
  }

  @Test
  fun testImagePlaceholderIsTheAltTextOnOneLine() {
    assertEquals("alt text", images("![alt text](image.png)").single().placeholderText)
    assertEquals("first second", images("![  first\n  second  ](image.png)").single().placeholderText)
  }

  @Test
  fun testImageWithoutAltTextUsesTheGenericPlaceholder() {
    val placeholder = MarkdownBundle.message("markdown.live.preview.image.placeholder")

    assertEquals(placeholder, images("![](image.png)").single().placeholderText)
    assertEquals(placeholder, images("![   ](image.png)").single().placeholderText)
  }

  @Test
  fun testOnlyAClosedMermaidFenceIsSpec() {
    val fence = "```mermaid\ngraph LR\n  A --> B\n```"
    val content = "Some `mermaid` code\n\n```\nval a = 1\n```\n\n$fence\n\n```mermaid\nopen\n"
    (val range, val source) = elements(content).filterIsInstance<MarkdownLivePreviewSpec.CodeFence>().single()

    assertEquals(fence, content.substring(range.startOffset, range.endOffset))
    assertEquals("graph LR\n  A --> B", source)
  }

  @Test
  fun testImagesBetweenHeadersAreBothSpecs() {
    val content = "# Markdown WYSIWYG Demo\n\n![logo](image.png)\n\n![logo](image.png)\n\n# Markdown WYSIWYG Demo"

    assertEquals(2, images(content).size)
  }

  @Test
  fun testAdjacentImagesAreBothSpecs() {
    assertEquals(2, images("![logo](test.png)\n![logo](test.png)").size)
  }

  @Test
  fun testInlineAndNestedImagesConcealTheirElement() {
    assertEquals(listOf("![alt](image.png)"), imageRanges("Text ![alt](image.png) here"))
    assertEquals(listOf("![alt](image.png)"), imageRanges("- ![alt](image.png)"))
    assertEquals(listOf("![alt](image.png)"), imageRanges("> ![alt](image.png)"))
    assertEquals(listOf("![alt](image.png)"), imageRanges("# ![alt](image.png)"))
  }

  @Test
  fun testImageLineInsideParagraphConcealsItsCompleteLine() {
    assertEquals(listOf("  ![alt](image.png) "), imageRanges("before\n  ![alt](image.png) \nafter"))
  }

  @Test
  fun testTwoImagesOnOneLineConcealTheirElements() {
    val content = "![one](one.png) ![two](two.png)"
    val images = images(content)

    assertEquals(listOf("one.png", "two.png"), images.map { it.destination })
    assertEquals(listOf("one", "two"), images.map { it.placeholderText })
    assertEquals(listOf("![one](one.png)", "![two](two.png)"), imageRanges(content))
  }

  @Test
  fun testReferenceImagesUseTheDestinationOfTheirDefinition() {
    val content = "![full][image] ![image]\n\n![missing][none]\n\n[image]: <image.png>"
    val images = images(content)

    assertEquals(listOf("image.png", "image.png"), images.map { it.destination })
    assertEquals(listOf("full", MarkdownBundle.message("markdown.live.preview.image.placeholder")), images.map { it.placeholderText })
    assertEquals(listOf("![full][image]", "![image]"), imageRanges(content))
  }

  @Test
  fun testRemoteAndDataImagesAreSpecsWithoutSource() {
    val logo = "https://upload.wikimedia.org/wikipedia/commons/4/4f/SVG_Logo.svg"
    val badge = "https://img.shields.io/badge/markdown-preview-blue.svg"
    val data = "data:image/png;base64,AAAA"
    val content = "![svg image]($logo)\n\n![svg image]($badge)(https://example.com)\n\n![dot](HTTP://example.org/a.png) ![dot]($data)"
    val images = images(content)

    assertEquals(listOf(logo, badge, "HTTP://example.org/a.png", data), images.map { it.destination })
    assertEquals(listOf("svg image", "svg image", "dot", "dot"), images.map { it.placeholderText })
    assertTrue(images.all { it.isBrowserOwned })
    assertTrue(images.all { it.source == null }, "The backend loads no browser-owned image")
    assertEquals(
      listOf("![svg image]($logo)", "![svg image]($badge)", "![dot](HTTP://example.org/a.png)", "![dot]($data)"),
      imageRanges(content),
    )
    val other = images("![a](//example.org/a.png) ![b](data:text/plain,text)")
    assertEquals(listOf("//example.org/a.png", "data:text/plain,text"), other.map { it.destination })
  }

  @Test
  fun testImagesInUntrustedProjectHaveTheUntrustedFailure() {
    TrustedProjectsTestUtil.withTrustedProjectsCheckEnabled {
      TrustedProjects.setProjectTrusted(myFixture.project, false)
      try {
        val images = images("![local](image.png)\n\n![remote](https://example.org/image.png)")
        assertEquals(listOf("image.png", "https://example.org/image.png"), images.map { it.destination })
        assertEquals(listOf(MarkdownLivePreviewSpec.ImageFailure.UNTRUSTED), images.map { it.failure }.distinct())
        assertTrue(images.all { it.source == null })
      }
      finally {
        TrustedProjects.setProjectTrusted(myFixture.project, true)
      }
    }
  }

  @Test
  fun testFileUriIsALocalImageDestination() {
    (val destination, val isBrowserOwned) = images("![alt](file:///project/image.png)").single()

    assertEquals("file:///project/image.png", destination)
    assertFalse(isBrowserOwned)
  }

  @Test
  fun testReferenceLinksAreConcealed() {
    val content = """
      |[Markdown Guide][docs] [IntelliJ IDEA][] [GitHub]
      |
      |[docs]: https://example.org
      |[IntelliJ IDEA]: https://www.jetbrains.com/idea/
      |[GitHub]: https://github.com
    """.trimMargin()

    assertEquals(listOf("[", "][docs]", "[", "][]", "[", "]"), concealed(content))
    assertEquals(listOf("[Markdown Guide][docs]", "[IntelliJ IDEA][]", "[GitHub]"), revealRanges(content))
  }

  @Test
  fun testCodeFenceConcealsItsMarkersAndKeepsTheLanguage() {
    val content = """
      |```markdown
      |**bold** and [docs](https://example.org)
      |```
    """.trimMargin()
    assertEquals(listOf("```", "```"), concealed(content))
    assertEquals(listOf("```", "```"), concealed("```\nplain\n```"))
    assertEquals(listOf("~~~", "~~~"), concealed("~~~python\nprint(1)\n~~~"))
    assertEquals(listOf("```", "```"), concealed("  ```kotlin\n  val a = 1\n  ```"))
    assertEquals(listOf("```"), concealed("```kotlin\nopen\n"))
    assertEquals(listOf("```", " title=\"123\"", "```"), concealed("```java title=\"123\"\ncode\n```"))
    assertEquals(listOf("``` ", "```"), concealed("``` bash\necho one\n```"))
  }

  @Test
  fun testCodeFenceLayoutHasTheStartOfTheLanguageAndOfEachCodeLine() {
    val content = "- item\n\n  ```bash\n  echo one\n\n    echo two\n  ```"
    val layout = elements(content).filterIsInstance<MarkdownLivePreviewSpec.CodeFenceLayout>().single()

    assertEquals(content.indexOf("```"), layout.markerStart)
    assertEquals(content.indexOf("bash"), layout.languageStart)
    assertEquals(listOf(content.indexOf("echo one"), content.indexOf("  echo two")), layout.codeStarts)
  }

  @Test
  fun testUnclosedCodeFenceHasNoLayout() {
    assertTrue(elements("```bash\necho one\n\ntext").none { it is MarkdownLivePreviewSpec.CodeFenceLayout })
    assertNull(elements("```\necho one\n```").filterIsInstance<MarkdownLivePreviewSpec.CodeFenceLayout>().single().languageStart)
  }

  @Test
  fun testIndentedCodeBlockContentIsNotConcealed() {
    assertThat(concealed("text\n\n    **bold**\n")).isEmpty()
  }

  @Test
  fun testUnbalancedMarkersAreNotConcealed() {
    assertThat(concealed("**not bold and `not code")).isEmpty()
  }

  @Test
  fun testEmptyEmphasisIsNotConcealed() {
    assertThat(concealed("**** and ``")).isEmpty()
  }

  @Test
  fun testNestedEmphasisRevealRangesAreNested() {
    val content = "**bold *and italic* here**"
    val elements = elements(content)
    assertEquals(2, elements.size)
    val outer = elements[0]
    val inner = elements[1]
    assertEquals("**bold *and italic* here**", content.substring(outer.range.startOffset, outer.range.endOffset))
    assertEquals("*and italic*", content.substring(inner.range.startOffset, inner.range.endOffset))
    assertTrue(outer.range.contains(inner.range), "The inner element must be contained in the outer one")
  }

  @Test
  fun testHeaderInlineMarkersAreConcealedInsideTheHeading() {
    assertEquals(listOf("### Deep *header*", "*", "*"), concealed("### Deep *header*"))
  }

  @Test
  fun testTopLevelAtxHeadingsCoverTheirLines() {
    val content = "# One\n## Two\n### Three\n#### Four\n##### Five\n###### Six"
    val headings = headings(content)

    assertEquals((1..6).toList(), headings.map { it.level })
    assertEquals(listOf("One", "Two", "Three", "Four", "Five", "Six"), headings.map { it.body().text() })
    assertEquals(content.lines(), headings.map { content.substring(it.range.startOffset, it.range.endOffset) })
  }

  @Test
  fun testHeadingHtmlHidesMarkersIndentAndLinkDestinations() {
    val content = "before\n  ### [visible](https://example.org) text ###  \n\nafter"
    val heading = headings(content).single()

    assertEquals("  ### [visible](https://example.org) text ###  ", content.substring(heading.range.startOffset, heading.range.endOffset))
    assertEquals("visible text", heading.body().text())
    assertEquals("https://example.org", heading.body().select("a").attr("href"))
  }

  @Test
  fun testHeadingHtmlRendersInlineStyles() {
    val body = headings("# **bold** *em* `code` ~~gone~~ [link](https://example.org)").single().body()

    assertEquals("bold", body.select("strong").text())
    assertEquals("em", body.select("em").text())
    assertEquals("code", body.select("code").text())
    assertEquals("gone", body.select(".user-del").text())
    assertEquals("link", body.select("a").text())
    assertEquals("bold em code gone link", body.text())
  }

  @Test
  fun testHeadingHtmlMarksHighlightWithASpanClass() {
    val body = headings("# a ==marked== b").single().body()

    assertEquals("marked", body.select(".user-mark").text())
    assertThat(body.select("mark")).isEmpty()
    assertEquals("a marked b", body.text())
  }

  @Test
  fun testHeadingKeepsRawHtmlAndMathAsText() {
    val content = $$"# Use <kbd>Ctrl</kbd> if $a<b$"
    val body = headings(content).single().body()

    assertThat(body.select("kbd")).isEmpty()
    val tag = body.select("span[md-src-pos]").first { it.wholeText() == "<kbd>" }
    val [start, end] = tag.attr("md-src-pos").split("..").map(String::toInt)
    assertEquals("<kbd>", content.substring(start, end))
    assertTrue(body.text().startsWith("Use <kbd>Ctrl</kbd> if "), body.text())
    assertTrue(body.text().contains("a<b"), body.text())
  }

  @Test
  fun testHeadingDropsImagesAndKeepsTheImageSpec() {
    val content = "# ![logo](logo.png) Title"

    assertEquals("Title", headings(content).single().body().text())
    assertThat(headings(content).single().body().select("img")).isEmpty()
    assertEquals(1, images(content).size)
  }

  @Test
  fun testHeadingSpansCoverTheirSource() {
    val content = "before\n# Hello **big** (world): *yes* & \\*no\\*"
    val heading = headings(content).single()
    val line = content.substring(heading.range.startOffset, heading.range.endOffset)
    val spans = heading.body().select("span[md-src-pos]")

    assertEquals("Hello big (world): yes & *no*", spans.joinToString("") { it.wholeText() })
    for (span in spans.filter { it.wholeText().none { char -> char == '&' || char == '*' } }) {
      val [start, end] = span.attr("md-src-pos").split("..").map(String::toInt)
      assertEquals(span.wholeText(), line.substring(start, end), span.toString())
    }
  }

  @Test
  fun testHeadingHtmlDoesNotDependOnTheTextBeforeTheHeading() {
    assertEquals(headings("# Hello **big**").single().html, headings("some text\n\n# Hello **big**").single().html)
  }

  @Test
  fun testSetextHeadingsCoverTheirContentLineAndRulesCoverTheirUnderlines() {
    val content = "One\n===\n\nTwo\n---\n\nThree *text*\n====="
    val headings = headings(content)
    val rules = elements(content).filterIsInstance<MarkdownLivePreviewSpec.HorizontalRule>()

    assertEquals(listOf(1, 2, 1), headings.map { it.level })
    assertEquals(listOf("One", "Two", "Three *text*"), headings.map { content.substring(it.range.startOffset, it.range.endOffset) })
    assertEquals(listOf("One", "Two", "Three text"), headings.map { it.body().text() })
    assertEquals(listOf("===", "---", "====="), rules.map { content.substring(it.range.startOffset, it.range.endOffset) })
  }

  /** The parser takes only one line before the underline. If it learns more, the heading spec must cover all those lines. */
  @Test
  fun testMultiLineSetextContentIsNotAHeading() {
    assertThat(headings("Multi\nline\n=====")).isEmpty()
    assertThat(headings("Multi\nline\n-----")).isEmpty()
  }

  @Test
  fun testSetextHeadingSpansCoverTheirSource() {
    val content = "before\n\n  Hello **big** (world)\n---"
    val heading = headings(content).single()
    val lines = content.substring(heading.range.startOffset, heading.range.endOffset)
    val spans = heading.body().select("span[md-src-pos]")

    assertEquals("  Hello **big** (world)", lines)
    assertEquals("Hello big (world)", spans.joinToString("") { it.wholeText() }.trim())
    for (span in spans) {
      val [start, end] = span.attr("md-src-pos").split("..").map(String::toInt)
      assertEquals(span.wholeText(), lines.substring(start, end), span.toString())
    }
  }

  @Test
  fun testListAndUnsupportedHeadingsAreNotReported() {
    val content = """
      |- # list heading
      |
      |```markdown
      |### code heading
      |```
      |<section>
      |#### html heading
      |</section>
      |
      |> - ##### quoted list heading
      |
      |####### too deep
      |
      |    # indented code
      |
      |# top-level heading
    """.trimMargin()
    (val range, val level) = headings(content).single()

    assertEquals(1, level)
    assertEquals("# top-level heading", content.substring(range.startOffset, range.endOffset))
  }

  @Test
  fun testQuotedAndAlertHeadingsCoverTheirLines() {
    val content = """
      |> # one
      |
      |> > ## two
      |
      |  >### three
      |
      |> [!NOTE]
      |> #### four
      |
      |> Quoted *five*
      |> ---
    """.trimMargin()
    val headings = headings(content)
    val lines = headings.map { content.substring(it.range.startOffset, it.range.endOffset) }

    assertEquals(listOf("> # one", "> > ## two", "  >### three", "> #### four", "> Quoted *five*"), lines)
    assertEquals(listOf(1, 2, 3, 4, 2), headings.map { it.level })
    assertEquals(listOf("one", "two", "three", "four", "Quoted five"), headings.map { it.body().text() })
    for ([heading, line] in headings.zip(lines)) {
      for (span in heading.body().select("span[md-src-pos]")) {
        val [start, end] = span.attr("md-src-pos").split("..").map(String::toInt)
        assertEquals(span.wholeText(), line.substring(start, end), span.toString())
      }
    }
  }

  @Test
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
    assertEquals(listOf("•", "◦", "▪", "•"), elements.filterIsInstance<MarkdownLivePreviewSpec.Bullet>().map { it.placeholderText })
  }

  @Test
  fun testOrderedListParentsCountTowardsBulletDepth() {
    val content = "1. one\n   - two"
    val elements = elements(content)
    assertEquals(listOf("-"), concealed(content))
    assertEquals(listOf("◦"), elements.filterIsInstance<MarkdownLivePreviewSpec.Bullet>().map { it.placeholderText })
  }

  @Test
  fun testOrderedListMarkersAreNotConcealed() {
    assertThat(elements("1. ordered").filterNot { it is MarkdownLivePreviewSpec.ListIndent }).isEmpty()
  }

  @Test
  fun testListLinesGetIndentSpecsWithTheirDepth() {
    assertEquals(listOf(0 to 1, 1 to 2, 2 to 3, 3 to 2), listIndents("- one\n  * two\n    + three\n  * two again"))
  }

  @Test
  fun testTopLevelListLinesGetIndentSpecsAndParagraphsDoNot() {
    assertEquals(listOf(2 to 1, 3 to 1, 4 to 1), listIndents("text\n\n1. one\n   continuation\n2. two\n\ntext"))
  }

  @Test
  fun testNestedItemContinuationLinesGetTheDepthOfTheirItem() {
    val content = "- one\n  - two\n    continuation\n\n    second paragraph\n  - three"
    assertEquals(listOf(0 to 1, 1 to 2, 2 to 2, 4 to 2, 5 to 2), listIndents(content))
  }

  @Test
  fun testOrderedNestedListsGetIndentSpecs() {
    assertEquals(listOf(0 to 1, 1 to 2, 2 to 3), listIndents("1. one\n   1. two\n      - three"))
  }

  @Test
  fun testLazyAndQuoteMarkedLinesGetNoIndentSpec() {
    assertEquals(listOf(0 to 1, 1 to 1, 3 to 1), listIndents("- one\n  continuation\n\n- two"))
    assertEquals(listOf(0 to 1, 1 to 2), listIndents("- one\n  - two\nlazy"))
    assertThat(listIndents("> - a\n>   - b")).isEmpty()
  }

  @Test
  fun testLineThatStartsInAnOuterItemGetsTheDepthOfThatItem() {
    assertEquals(listOf(0 to 1, 1 to 2, 2 to 2), listIndents("- a\n  - b\n    > - c"))
  }

  @Test
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

  @Test
  fun testNestedTaskAndQuotedTaskPreserveTheirIndentationAndQuoteMarkers() {
    val content = "- [ ] parent\n  continuation\n  - [x] child\n\n> - [ ] quoted"
    assertEquals(listOf("- [ ]", "- [x]", ">", "- [ ]"), concealed(content))
    assertEquals(listOf("- [ ]", "- [x]", ">", "- [ ]"), revealRanges(content))
  }

  @Test
  fun testBlockquotesConcealAndRevealEachMarker() {
    val content = "> first\n> second\n>\n> last"
    assertEquals(listOf(">", ">", ">", ">"), concealed(content))
    assertEquals(listOf(">", ">", ">", ">"), revealRanges(content))
  }

  @Test
  fun testAdjacentBlockquoteMarkersOfALineRevealTogether() {
    val content = ">>text\n>>>>text\n> > > test"
    assertEquals(List(9) { ">" }, concealed(content))
    assertEquals(
      listOf(">>", ">>", ">>>>", ">>>>", ">>>>", ">>>>", "> > >", "> > >", "> > >"),
      revealRanges(content),
    )
  }

  @Test
  fun testListMarkerBetweenBlockquoteMarkersSplitsTheirRun() {
    val content = "> - > quoted item"
    assertEquals(listOf(">", "-", ">"), concealed(content))
    assertEquals(listOf(">", "-", ">"), revealRanges(content))
  }

  @Test
  fun testBlockquoteRulesCoverTheLinesOfTheirMarkers() {
    val content = "> first\n> second\n>\n> last"
    assertEquals(listOf("> first\n", "> second\n", ">\n", "> last"), ruleSegments(content))
  }

  @Test
  fun testNestedBlockquoteRulesCoverTheLinesOfTheirQuote() {
    val content = "> outer\n> > inner\n> outer"
    assertEquals(listOf("> outer\n", "> > inner\n", "> inner", "> outer"), ruleSegments(content))
  }

  @Test
  fun testBlockquoteRuleCoversLazyContinuationLines() {
    val content = "> first\nlazy\n> third"
    assertEquals(listOf("> first", "> third"), ruleSegments(content))
  }

  @Test
  fun testBlockquoteRulesInsideListItemsCoverTheirLines() {
    val content = "- > first\n  > second"
    assertEquals(listOf("> first\n", "> second"), ruleSegments(content))
  }

  @Test
  fun testNestedBlockquotesHaveNestedRanges() {
    val content = "> outer\n> > inner\n> outer"
    assertEquals(listOf(">", ">", ">", ">"), concealed(content))
    assertEquals(listOf(">", "> >", "> >", ">"), revealRanges(content))
  }

  @Test
  fun testBlockquoteMarkersCanChangeIndentation() {
    val content = "> first\n  > second\n> third"
    assertEquals(listOf(">", ">", ">"), concealed(content))
    assertEquals(listOf(">", ">", ">"), revealRanges(content))
  }

  @Test
  fun testNestedBlockquoteRangeStopsAtTheOuterSibling() {
    val content = "> Bad example. More messages\n> > Another example\n> Test"
    assertEquals(listOf(">", ">", ">", ">"), concealed(content))
    assertEquals(listOf("> Bad example. More messages\n", "> > Another example\n", "> Another example", "> Test"), ruleSegments(content))
  }

  @Test
  fun testBlockquoteContinuationMarkersAreConcealed() {
    val content = "> first\n> second\n> third"
    assertEquals(listOf(">", ">", ">"), concealed(content))
    assertEquals(listOf(">", ">", ">"), revealRanges(content))
  }

  @Test
  fun testBlockquoteMarkersInsideListItemsAreConcealed() {
    val content = "- > first\n  > second\n  > third"
    assertEquals(listOf("-", ">", ">", ">"), concealed(content))
    assertEquals(listOf("-", ">", ">", ">"), revealRanges(content))
  }

  @Test
  fun testBlockquoteMarkersAfterTaskCheckboxesAreConcealed() {
    val content = "- [ ] > first\n  > second"
    assertEquals(listOf("- [ ]", ">", ">"), concealed(content))
  }

  @Test
  fun testBlockquoteMarkersInsidePlusListItemsAreConcealed() {
    val content = "+ > first\n  > second\n  > third"
    assertEquals(listOf("+", ">", ">", ">"), concealed(content))
    assertEquals(listOf("+", ">", ">", ">"), revealRanges(content))
  }

  @Test
  fun testBlockquoteMarkersInsideAsteriskListItemsAreConcealed() {
    val content = "* > first\n  > second\n  > third"
    assertEquals(listOf("*", ">", ">", ">"), concealed(content))
    assertEquals(listOf("*", ">", ">", ">"), revealRanges(content))
  }

  @Test
  fun testBlockquoteMarkersInsideOrderedListItemsAreConcealed() {
    val content = "1. > first\n   > second\n   > third"
    assertEquals(listOf(">", ">", ">"), concealed(content))
    assertEquals(listOf(">", ">", ">"), revealRanges(content))
  }

  @Test
  fun testBlockquoteMarkersInsideParenthesizedListItemsAreConcealed() {
    val content = "12) > first\n    > second\n    > third"
    assertEquals(listOf(">", ">", ">"), concealed(content))
    assertEquals(listOf(">", ">", ">"), revealRanges(content))
  }

  @Test
  fun testManySeparateBlockquotes() {
    val quotes = (1..200).map { "> quote $it" }
    val content = quotes.joinToString("\n\n")
    assertEquals(List(200) { ">" }, concealed(content))
    assertEquals(List(200) { ">" }, revealRanges(content))
    assertEquals(quotes, ruleSegments(content))
  }

  @Test
  fun testDeepBlockquotesConcealEachMarkerOnce() {
    val prefix = "> ".repeat(64)
    val quote = "${prefix}first\n${prefix}second"
    val content = "$quote\n" + "continuation\n".repeat(200)
    assertEquals(List(128) { ">" }, concealed(content))
    assertEquals(List(128) { prefix.trimEnd() }, revealRanges(content))
  }

  @Test
  fun testSiblingNestedBlockquotesKeepSeparateMarkers() {
    val content = "> > first\n>\n> > second"
    assertEquals(listOf(">", ">", ">", ">", ">"), concealed(content))
    assertEquals(listOf("> > first\n", "> first", ">\n", "> > second", "> second"), ruleSegments(content))
    assertEquals(listOf("> >", "> >", ">", "> >", "> >"), revealRanges(content))
  }

  @Test
  fun testAlertsConcealTheirMarkersAndTitleBrackets() {
    val content = "> [!NOTE]\n> text"
    assertEquals(listOf(">", "[!", "]", ">"), concealed(content))
    assertEquals(listOf(">", "[!NOTE]", ">"), revealRanges(content))
    assertEquals(listOf("> [!NOTE]\n", "> text"), ruleSegments(content))
    assertEquals(listOf(AlertType.NOTE, AlertType.NOTE), blockQuotes(content).map { it.alertType })
  }

  @Test
  fun testQuoteInsideAnAlertHasNoAlertType() {
    val content = "> [!CAUTION]\n> > nested"
    assertEquals(listOf(AlertType.CAUTION, AlertType.CAUTION, null), blockQuotes(content).map { it.alertType })
  }

  @Test
  fun testUnknownAlertTitleHasNoAlertType() {
    assertEquals(listOf(null), blockQuotes("> [!UNKNOWN]").map { it.alertType })
  }

  @Test
  fun testTaskExamplesOutsideListsAreNotCheckboxes() {
    val content = "[ ] plain\n[x] plain\n\n```markdown\n- [ ] example\n```\n\n    - [x] code\n\n`- [ ] inline`"
    assertThat(elements(content).filterIsInstance<MarkdownLivePreviewSpec.TaskCheckbox>()).isEmpty()
  }

  @Test
  fun testThematicBreaksConcealTheirCompleteLines() {
    val content = "---\n***\n___\n  *  *  *  \ntail"
    val breaks = elements(content).filterIsInstance<MarkdownLivePreviewSpec.HorizontalRule>()

    assertEquals(4, breaks.size)
    assertEquals(listOf("---", "***", "___", "  *  *  *  "), breaks.map { content.substring(it.range.startOffset, it.range.endOffset) })
    assertTrue(breaks.all { it.range.length > 0 })
  }

  @Test
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

  @Test
  fun testFrontMatterDelimitersWithTrailingWhitespaceAreConcealed() {
    val content = "---  \nname: valid-skill\n---\t\n"
    val breaks = elements(content).filterIsInstance<MarkdownLivePreviewSpec.HorizontalRule>()

    assertEquals(listOf("---  ", "---\t"), breaks.map { content.substring(it.range.startOffset, it.range.endOffset) })
  }

  @Test
  fun testCodeFontCoversCodeButNotItsMarkupOrFootnoteContinuations() {
    val content = """
      |Call `foo()` here
      |
      |    indented
      |
      |```kotlin
      |val a = 1
      |```
      |
      |~~~python
      |print(1)
      |~~~
      |
      |```
      |plain
      |```
      |
      |~~~
      |plain tilde
      |~~~
      |
      |> ```java
      |> quoted
      |> ```
      |
      |> ~~~yaml
      |> quoted: tilde
      |> ~~~
      |
      |- item
      |  ```sh
      |  listed
      |  ```
      |
      |- item
      |  ~~~ruby
      |  listed tilde
      |  ~~~
      |
      |[^note]: First paragraph.
      |
      |    continuation
    """.trimMargin()
    val codeRanges = elements(content).filterIsInstance<MarkdownLivePreviewSpec.CodeFont>().flatMap { it.codeRanges }

    assertEquals(
      listOf(
        "foo()",
        "    indented",
        "```", "kotlin", "val a = 1", "```",
        "~~~", "python", "print(1)", "~~~",
        "```", "plain", "```",
        "~~~", "plain tilde", "~~~",
        "```", "java", "quoted", "```",
        "~~~", "yaml", "quoted: tilde", "~~~",
        "```", "sh", "listed", "```",
        "~~~", "ruby", "listed tilde", "~~~",
      ),
      codeRanges.map { [start, end] -> content.substring(start, end) },
    )
  }

  @Test
  fun testCodeFontCoversTheIndentOfEveryLineOfAnIndentedFence() {
    val content = "\t```bash\n\tgit pull\n\t```"
    val codeRanges = elements(content).filterIsInstance<MarkdownLivePreviewSpec.CodeFont>().flatMap { it.codeRanges }

    assertEquals(listOf("\t```", "bash", "\tgit pull", "\t```"), codeRanges.map { [start, end] -> content.substring(start, end) })
  }

  @Test
  fun testDocumentVersionUsesLocalStampWithoutPatchVersion() {
    myFixture.configureByText("test.md", "text")
    val document = myFixture.editor.document as DocumentEx
    val version = MarkdownLivePreviewDocumentVersion.capture(document, myFixture.project)

    assertTrue(version.matches(document, myFixture.project))
    document.setModificationStamp(document.modificationStamp + 1)
    assertFalse(version.matches(document, myFixture.project))
  }

  @Test
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

  @Test
  fun testProhibitedImagesHaveNoSpecs() {
    val noImages = MarkdownLivePreviewConfiguration.DEFAULT_LIVE_PREVIEW_CONFIGURATION.copy(
      prohibited = setOf(MarkdownLivePreviewConfiguration.IMAGES)
    )

    assertThat(images("![local](image.png) ![remote](https://example.org/image.png)", noImages)).isEmpty()
    assertThat(concealed("![alt](image.png)", noImages)).isEmpty()
  }

  @Test
  fun testHeadingKeepsTheImageSourceAsTextWhenImagesAreProhibited() {
    val content = "# ![logo](logo.png) Title"
    val noImages = MarkdownLivePreviewConfiguration.DEFAULT_LIVE_PREVIEW_CONFIGURATION.copy(
      prohibited = setOf(MarkdownLivePreviewConfiguration.IMAGES)
    )
    val body = headings(content, noImages).single().body()

    assertEquals("![logo](logo.png) Title", body.text())
    val image = body.select("span[md-src-pos]").first { it.wholeText() == "![logo](logo.png)" }
    val [start, end] = image.attr("md-src-pos").split("..").map(String::toInt)
    assertEquals("![logo](logo.png)", content.substring(start, end))
  }

  @Test
  fun testProhibitedCodeFenceLanguagesHaveNoSpecs() {
    val content = "```mermaid\ngraph LR\n```\n\n```plantuml\nA -> B\n```"
    val configuration = MarkdownLivePreviewConfiguration.DEFAULT_LIVE_PREVIEW_CONFIGURATION.copy(
      prohibited = setOf(MarkdownLivePreviewConfiguration.MERMAID)
    )
    val languages = elements(content, configuration).filterIsInstance<MarkdownLivePreviewSpec.CodeFence>().map { it.language }

    assertEquals(listOf("plantuml"), languages)
  }

  @Test
  fun testProhibitedCodeFenceKeepsItsMarkerConcealsAndCodeFont() {
    val content = "```mermaid\ngraph LR\n```"
    val configuration = MarkdownLivePreviewConfiguration.DEFAULT_LIVE_PREVIEW_CONFIGURATION.copy(
      prohibited = setOf(MarkdownLivePreviewConfiguration.MERMAID)
    )

    assertEquals(listOf("```", "```"), concealed(content, configuration))
    assertEquals(1, elements(content, configuration).filterIsInstance<MarkdownLivePreviewSpec.CodeFont>().size)
  }

  @Test
  fun testProhibitedHeadingsHaveNoSpecsAndKeepTheirInlineConceals() {
    val content = "### Deep *header*\n\nSetext\n===\n\n> # quoted"
    val noHeadings = MarkdownLivePreviewConfiguration.DEFAULT_LIVE_PREVIEW_CONFIGURATION.copy(
      prohibited = setOf(MarkdownLivePreviewConfiguration.HEADINGS)
    )

    assertEquals(listOf("*", "*", ">"), concealed(content, noHeadings))
  }

  @Test
  fun testProhibitedBlockQuotesHaveNoSpecsAndNoQuotedHeadings() {
    val content = "> [!NOTE]\n> *text*\n\n> # quoted\n\n# top"
    val noBlockQuotes = MarkdownLivePreviewConfiguration.DEFAULT_LIVE_PREVIEW_CONFIGURATION.copy(
      prohibited = setOf(MarkdownLivePreviewConfiguration.BLOCK_QUOTES)
    )

    assertEquals(listOf("*", "*", "# top"), concealed(content, noBlockQuotes))
  }

  private fun MarkdownLivePreviewSpec.concealedRanges(): List<MarkdownLivePreviewRange> = when (this) {
    is MarkdownLivePreviewSpec.Conceal -> conceals
    is MarkdownLivePreviewSpec.BlockQuote -> listOf(markerRange)
    is MarkdownLivePreviewSpec.HorizontalRule -> listOf(range)
    is MarkdownLivePreviewSpec.Heading -> listOf(range)
    is MarkdownLivePreviewSpec.Image -> listOf(range)
    is MarkdownLivePreviewSpec.CodeFence -> emptyList()
    is MarkdownLivePreviewSpec.CodeFenceLayout -> emptyList()
    is MarkdownLivePreviewSpec.CodeFont -> emptyList()
    is MarkdownLivePreviewSpec.Bullet -> listOf(range)
    is MarkdownLivePreviewSpec.TaskCheckbox -> listOf(range)
    is MarkdownLivePreviewSpec.ListIndent -> emptyList()
  }

  private fun elements(
    content: String,
    configuration: MarkdownLivePreviewConfiguration = MarkdownLivePreviewConfiguration.DEFAULT_LIVE_PREVIEW_CONFIGURATION,
  ): List<MarkdownLivePreviewSpec> {
    myFixture.configureByText("test.md", content)
    val elements = timeoutRunBlocking { readAction { computeLivePreviewSpecs(myFixture.file, myFixture.editor, configuration) } }.elements
    for (element in elements) {
      val conceals = element.concealedRanges()
      if (element is MarkdownLivePreviewSpec.ListIndent) {
        val offset = element.range.startOffset
        assertTrue(element.range.isEmpty && (offset == 0 || content[offset - 1] == '\n'), "An indent must be empty and sit at a line start: $element")
      }
      else if (element !is MarkdownLivePreviewSpec.CodeFence && element !is MarkdownLivePreviewSpec.CodeFenceLayout &&
               element !is MarkdownLivePreviewSpec.CodeFont) {
        assertFalse(conceals.isEmpty(), "An element with nothing to conceal must not be reported: $element")
      }
      assertTrue(conceals.all { element.range.contains(it) }, "An element must contain the markup it conceals: $element")
      if (element is MarkdownLivePreviewSpec.BlockQuote) {
        assertEquals(element.markerRange.startOffset, element.ruleRange.startOffset, "A rule must start at its marker: $element")
      }
    }
    val ranges = elements.flatMap { it.concealedRanges() }
    assertEquals(ranges.distinct(), ranges, "Each range must be concealed only once")
    return elements
  }

  private fun concealed(
    content: String,
    configuration: MarkdownLivePreviewConfiguration = MarkdownLivePreviewConfiguration.DEFAULT_LIVE_PREVIEW_CONFIGURATION,
  ) =
    elements(content, configuration).flatMap { it.concealedRanges() }
      .map { (val startOffset, val endOffset) -> content.substring(startOffset, endOffset) }

  private fun revealRanges(content: String): List<String> =
    elements(content)
      .filterNot { it is MarkdownLivePreviewSpec.ListIndent || it is MarkdownLivePreviewSpec.CodeFont || it is MarkdownLivePreviewSpec.CodeFenceLayout }
      .map { content.substring(it.range.startOffset, it.range.endOffset) }

  /** The line number and the depth of each list indent, in document order. */
  private fun listIndents(content: String): List<Pair<Int, Int>> =
    elements(content).filterIsInstance<MarkdownLivePreviewSpec.ListIndent>().map { (val range, val depth) ->
      myFixture.editor.document.getLineNumber(range.startOffset) to depth
    }

  private fun ruleSegments(content: String): List<String> =
    blockQuotes(content).map { content.substring(it.ruleRange.startOffset, it.ruleRange.endOffset) }

  private fun blockQuotes(content: String): List<MarkdownLivePreviewSpec.BlockQuote> =
    elements(content).filterIsInstance<MarkdownLivePreviewSpec.BlockQuote>()

  private fun images(
    content: String,
    configuration: MarkdownLivePreviewConfiguration = MarkdownLivePreviewConfiguration.DEFAULT_LIVE_PREVIEW_CONFIGURATION,
  ) =
    elements(content, configuration).filterIsInstance<MarkdownLivePreviewSpec.Image>()

  private fun headings(
    content: String,
    configuration: MarkdownLivePreviewConfiguration = MarkdownLivePreviewConfiguration.DEFAULT_LIVE_PREVIEW_CONFIGURATION,
  ) =
    elements(content, configuration).filterIsInstance<MarkdownLivePreviewSpec.Heading>()

  private fun MarkdownLivePreviewSpec.Heading.body(): Element = Jsoup.parseBodyFragment(html).body()

  private fun imageRanges(content: String): List<String> =
    images(content).map { content.substring(it.range.startOffset, it.range.endOffset) }

  private fun MarkdownLivePreviewRange.contains(other: MarkdownLivePreviewRange): Boolean {
    return startOffset <= other.startOffset && endOffset >= other.endOffset
  }

}
