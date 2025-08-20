// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.backend

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.impl.InlayProvider
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.backend.hyperlinks.BackendTerminalHyperlinkFacade
import com.intellij.terminal.session.*
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.flow.*
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.updateContent
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.time.Duration.Companion.milliseconds

@RunWith(JUnit4::class)
internal class BackendTerminalHyperlinkHighlighterTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `no links`() = withFixture {
    updateModel(0L, """
      0: line0
      1: line1
    """.trimIndent())
    assertText("""
      0: line0
      1: line1
    """.trimIndent())
    assertLinks()
    assertHighlightings()
  }

  @Test
  fun `some links`() = withFixture {
    updateModel(0L, """
      0: line0 link0
      1: line1 link1
    """.trimIndent())
    assertText("""
      0: line0 link0
      1: line1 link1
    """.trimIndent())
    assertLinks(
      link(at(0, "link0")),
      link(at(1, "link1")),
    )
    assertHighlightings()
  }

  @Test
  fun `some links with inlays`() = withFixture {
    // Must have multiple results per line because com.intellij.execution.filters.CompositeFilter.createFinalResult
    // is broken and doesn't preserve the result item as-is if there's only one result.
    updateModel(0L, """
      0: line0 link_inlay01 link_inlay02
      1: line1 link_inlay11 link_inlay12
    """.trimIndent())
    assertText("""
      0: line0 link_inlay01 link_inlay02
      1: line1 link_inlay11 link_inlay12
    """.trimIndent())
    assertLinks(
      link(at(0, "link_inlay01")),
      link(at(0, "link_inlay02")),
      link(at(1, "link_inlay11")),
      link(at(1, "link_inlay12")),
    )
    assertInlays(
      inlay(at(0, "link_inlay01")),
      inlay(at(0, "link_inlay02")),
      inlay(at(1, "link_inlay11")),
      inlay(at(1, "link_inlay12")),
    )
    assertHighlightings()
  }

  @Test
  fun `remove line before processing`() = withFixture {
    updateModel(0L, """
      0: line0 link0
      1: line1 link1
    """.trimIndent())
    updateModel(1L, "")
    assertText("0: line0 link0\n")
    assertLinks(
      link(at(0, "link0")),
    )
    assertHighlightings()
  }

  @Test
  fun `remove line after processing`() = withFixture {
    updateModel(0L, """
      0: line0 link0
      1: line1 link1
    """.trimIndent())
    assertLinks(
      link(at(0, "link0")),
      link(at(1, "link1")),
    )
    updateModel(1L, "")
    assertText("0: line0 link0\n")
    assertLinks(
      link(at(0, "link0")),
    )
    assertHighlightings()
  }

  @Test
  fun `one click`() = withFixture {
    updateModel(0L, """
      0: line0 link0
      1: line1 link1
    """.trimIndent())
    assertClicks(
      at(0, "link0"),
    )
  }

  @Test
  fun `several clicks`() = withFixture {
    updateModel(0L, """
      0: line0 link0
      1: line1 link1
    """.trimIndent())
    assertClicks(
      at(0, "link0"),
      at(1, "link1"),
    )
  }

  @Test
  fun `several links per line`() = withFixture {
    updateModel(0L, """
      0: line0 link01 link02
      1: line1 link1
    """.trimIndent())
    assertText("""
      0: line0 link01 link02
      1: line1 link1
    """.trimIndent())
    assertLinks(
      link(at(0, "link01")),
      link(at(0, "link02")),
      link(at(1, "link1")),
    )
    assertHighlightings()
  }

  @Test
  fun `some highlighted links`() = withFixture {
    filter.highlight = HIGHLIGHT1
    filter.followedHighlight = HIGHLIGHT2
    filter.hoveredHighlight = HIGHLIGHT3
    updateModel(0L, """
      0: line0 link0
    """.trimIndent())
    assertLinks(
      link(at(0, "link0")),
    )
    filter.highlight = HIGHLIGHT2
    filter.followedHighlight = HIGHLIGHT3
    filter.hoveredHighlight = HIGHLIGHT4
    updateModel(1L, """
      1: line1 link1
    """.trimIndent())
    assertText("""
      0: line0 link0
      1: line1 link1
    """.trimIndent())
    assertLinks(
      link(at(0, "link0"), highlight = HIGHLIGHT1, followedHighlight = HIGHLIGHT2, hoveredHighlight = HIGHLIGHT3),
      link(at(1, "link1"), highlight = HIGHLIGHT2, followedHighlight = HIGHLIGHT3, hoveredHighlight = HIGHLIGHT4),
    )
    assertHighlightings()
  }

  @Test
  fun `some highlighting`() = withFixture {
    filter.highlight = HIGHLIGHT1
    updateModel(0L, """
      0: line0 highlight0
    """.trimIndent())
    assertHighlightings(
      highlight(at(0, "highlight0")),
    )
    filter.highlight = HIGHLIGHT2
    updateModel(1L, """
      1: line1 highlight1
    """.trimIndent())
    assertText("""
      0: line0 highlight0
      1: line1 highlight1
    """.trimIndent())
    assertLinks()
    assertHighlightings(
      highlight(at(0, "highlight0"), highlight = HIGHLIGHT1),
      highlight(at(1, "highlight1"), highlight = HIGHLIGHT2),
    )
  }

  @Test
  fun `some highlighting with inlays`() = withFixture {
    filter.highlight = HIGHLIGHT1
    // Must have multiple results per line because com.intellij.execution.filters.CompositeFilter.createFinalResult
    // is broken and doesn't preserve the result item as-is if there's only one result.
    updateModel(0L, """
      0: line0 highlight_inlay01 highlight_inlay02
      1: line1 highlight_inlay11 highlight_inlay12
    """.trimIndent())
    assertText("""
      0: line0 highlight_inlay01 highlight_inlay02
      1: line1 highlight_inlay11 highlight_inlay12
    """.trimIndent())
    assertHighlightings(
      highlight(at(0, "highlight_inlay01")),
      highlight(at(0, "highlight_inlay02")),
      highlight(at(1, "highlight_inlay11")),
      highlight(at(1, "highlight_inlay12")),
    )
    assertInlays(
      inlay(at(0, "highlight_inlay01")),
      inlay(at(0, "highlight_inlay02")),
      inlay(at(1, "highlight_inlay11")),
      inlay(at(1, "highlight_inlay12")),
    )
    assertLinks()
  }

  @Test
  fun `some links and some highlightings`() = withFixture {
    filter.highlight = HIGHLIGHT1
    updateModel(0L, """
      0: line0 highlight0
    """.trimIndent())
    assertLinks()
    assertHighlightings(
      highlight(at(0, "highlight0")),
    )
    filter.highlight = HIGHLIGHT2
    updateModel(1L, """
      1: line1 link1
    """.trimIndent())
    assertText("""
      0: line0 highlight0
      1: line1 link1
    """.trimIndent())
    assertLinks(
      link(at(1, "link1"), highlight = HIGHLIGHT2)
    )
    assertHighlightings(
      highlight(at(0, "highlight0"), highlight = HIGHLIGHT1),
    )
  }

  @Test
  fun `links update`() = withFixture {
    updateModel(0L, """
      0: line0 link0
      1: line1 link1
    """.trimIndent())
    assertLinks(
      link(at(0, "link0")),
      link(at(1, "link1")),
    )
    assertHighlightings()
    updateModel(1L, """
      1: line2 link2
    """.trimIndent())
    assertText("""
      0: line0 link0
      1: line2 link2
    """.trimIndent())
    assertLinks(
      link(at(0, "link0")),
      link(at(1, "link2")),
    )
    assertHighlightings()
  }

  @Test
  fun `many links, fast filter`() = withFixture {
    updateModel(0L, generateLines(0, 499, links = (0..499).toList()))
    assertLinks(
      *(0..499).map { link(at(it, "link${it}")) }.toTypedArray(),
    )
  }

  @Test
  fun `many links, slow filter, several updates`() = withFixture {
    filter.delayPerLine = 1
    updateModel(0L, generateLines(0, 499, links = (0..499).toList()))
    delay(50.milliseconds)
    updateModel(100L, generateLines(100, 199, links = (100..199).toList()))
    delay(50.milliseconds)
    updateModel(200L, generateLines(200, 299, links = (200..299).toList()))
    delay(50.milliseconds)
    updateModel(300L, generateLines(300, 399, links = (300..399).toList()))
    delay(50.milliseconds)
    updateModel(400L, generateLines(400, 499, links = (400..499).toList()))
    assertLinks(
      *(0..499).map { link(at(it, "link${it}")) }.toTypedArray(),
    )
    assertHighlightings()
  }

  @Test
  fun `sparse links, fast filter`() = withFixture {
    updateModel(0L, generateLines(0, 499, links = listOf(1, 100, 400)))
    assertLinks(
      link(at(1, "link1")),
      link(at(100, "link100")),
      link(at(400, "link400")),
    )
    assertHighlightings()
  }

  @Test
  fun `link trimming, fast filter`() {
    withFixture {
      updateModel(0L, generateLines(0, 499, links = (0..499).toList()))
      updateModel(500L, generateLines(500, 3999, links = (500..3999).toList()))
      assertLinks(
        *(0 until 667).map { link(at(it, "link${it + 3333}")) }.toTypedArray(),
      )
      assertHighlightings()
    }
  }

  @Test
  fun `link trimming, slow filter, just started`() {
    withFixture {
      filter.delayPerLine = 1
      updateModel(0L, generateLines(0, 499, links = (0..499).toList()))
      delay(100.milliseconds)
      // now the filter is still in progress
      updateModel(500L, generateLines(500, 3999, links = (500..3999).toList()))
      assertLinks(
        *(0 until 667).map { link(at(it, "link${it + 3333}")) }.toTypedArray(),
      )
      assertHighlightings()
    }
  }

  @Test
  fun `link trimming, slow filter, partially done`() {
    withFixture {
      filter.delayPerLine = 1
      updateModel(0L, generateLines(0, 499, links = (0..499).toList()))
      delay(400.milliseconds)
      // now the filter is still in progress, but should have emitted some links
      updateModel(500L, generateLines(500, 3999, links = (500..3999).toList()))
      assertLinks(
        *(0 until 667).map { link(at(it, "link${it + 3333}")) }.toTypedArray(),
      )
      assertHighlightings()
    }
  }

  @Test
  fun `link trimming, slow filter, two updates in the middle of a task, the second one is below the first one`() {
    withFixture {
      updateModel(0L, generateLines(0, 999, links = (0..999).toList()))
      delay(200.milliseconds) // now the task is definitely somewhere in the middle
      updateModel(998L, generateLines(1000, 1001, links = (1000..1001).toList()))
      updateModel(999L, generateLines(1002, 1005, links = (1002..1005).toList()))
      assertLinks(
        *(
          (0 until 764).map { link(at(it, "link${it + 234}")) } +
          listOf(
            link(at(764, "link1000")),
            link(at(765, "link1002")),
            link(at(766, "link1003")),
            link(at(767, "link1004")),
            link(at(768, "link1005")),
          )
        ).toTypedArray(),
      )
      assertHighlightings()
    }
  }

  @Test
  fun `link trimming, slow filter, two updates in the middle of a task, the second one is above the first one`() {
    withFixture {
      updateModel(0L, generateLines(0, 999, links = (0..999).toList()))
      delay(200.milliseconds) // now the task is definitely somewhere in the middle
      updateModel(998L, generateLines(1000, 1001, links = (1000..1001).toList()))
      updateModel(997L, generateLines(1002, 1004, links = (1002..1004).toList()))
      assertLinks(
        *(
          (0 until 766).map { link(at(it, "link${it + 231}")) } +
          listOf(
            link(at(766, "link1002")),
            link(at(767, "link1003")),
            link(at(768, "link1004")),
          )
        ).toTypedArray(),
      )
      assertHighlightings()
    }
  }

  private fun generateLines(from: Int, toInclusive: Int, links: List<Int>): String {
    val linksAt = links.toSet()
    return (from..toInclusive).joinToString("\n") { line ->
      "$line: ${if (line in linksAt) "link" else "line"}${line}"
    }
  }

  private fun withFixture(test: suspend Fixture.() -> Unit) = timeoutRunBlocking(coroutineName = "BackendTerminalHyperlinkHighlighterTest") {
    val fixture = Fixture(project)
    ExtensionTestUtil.maskExtensions<ConsoleFilterProvider>(
      ConsoleFilterProvider.FILTER_PROVIDERS,
      listOf(ConsoleFilterProvider { arrayOf(fixture.filter as Filter) }),
      testRootDisposable
    )
    withContext(Dispatchers.Default) {
      fixture.run(test)
    }
  }

  private class Fixture(private val project: Project) {

    private val outputModel = TerminalTestUtil.createOutputModel(MAX_LENGTH)
    private val document: Document get() = outputModel.document
    private lateinit var backendFacade: BackendTerminalHyperlinkFacade
    private val updateEvents = MutableSharedFlow<List<TerminalOutputEvent>>(replay = 100)
    private val pendingUpdateEventCount = MutableStateFlow(0)

    val filter = MyFilter()

    private val clickedLinks = mutableListOf<String>()

    suspend fun run(test: suspend Fixture.() -> Unit) {
      coroutineScope {
        val hyperlinkScope = childScope("BackendTerminalHyperlinkHighlighterTest hyperlink scope")
        backendFacade = BackendTerminalHyperlinkFacade(project, hyperlinkScope, outputModel, false)

        // do what StateAwareTerminalSession does, but with less infrastructure around
        val eventJob = launch(CoroutineName("BackendTerminalHyperlinkHighlighterTest event processing"), start = UNDISPATCHED) {
          merge(updateEvents, backendFacade.heartbeatFlow.map { listOf(it) }).collect { events ->
            events.forEach { event ->
              when (event) {
                is TerminalContentUpdatedEvent -> {
                  outputModel.updateContent(event)
                  pendingUpdateEventCount.update { it - 1 }
                }
                is TerminalHyperlinksHeartbeatEvent -> {
                  val modelUpdateEvent = backendFacade.collectResultsAndMaybeStartNewTask()
                  if (modelUpdateEvent != null) {
                    backendFacade.updateModelState(modelUpdateEvent)
                  }
                }
                else -> throw Exception("Event type is not used in the test: $event")
              }
            }
          }
        }

        test()

        // only for normal completion, otherwise test() is expected to throw
        eventJob.cancel()
        hyperlinkScope.cancel()
      }
    }

    suspend fun updateModel(fromLine: Long, newText: String) {
      updateEvents.emit(listOf(TerminalContentUpdatedEvent(newText, emptyList(), fromLine)))
      pendingUpdateEventCount.update { it + 1 }
    }

    suspend fun assertText(expected: String) {
      awaitEventProcessing()
      assertThat(document.text).isEqualTo(expected)
    }

    suspend fun assertLinks(vararg expectedLinks: Link) {
      awaitEventProcessing()
      val actualLinks = backendFacade.dumpState().hyperlinks.filterIsInstance<TerminalHyperlinkInfo>()
      assertThat(actualLinks).hasSameSizeAs(expectedLinks)
      for (i in actualLinks.indices) {
        val actual = actualLinks[i]
        val expected = expectedLinks[i]
        val actualStartOffset = outputModel.absoluteOffset(actual.absoluteStartOffset)
        val actualEndOffset = outputModel.absoluteOffset(actual.absoluteEndOffset)
        if (i == 0 && actualStartOffset.toRelative() < 0) continue // partially trimmed link, we may not be able to locate it
        val expectedStartOffset = outputModel.relativeOffset(expected.locator.locateOffset(document))
        val expectedEndOffset = outputModel.relativeOffset(expectedStartOffset.toRelative() + expected.locator.length)
        val expectedLayer = HighlighterLayer.HYPERLINK
        
        val description = "at $i actual link $actual expected link $expected"
        assertThat(actualStartOffset).`as`(description).isEqualTo(expectedStartOffset)
        assertThat(actualEndOffset).`as`(description).isEqualTo(expectedEndOffset)
        assertThat(actual.style).`as`(description).isEqualTo(expected.highlight)
        assertThat(actual.followedStyle).`as`(description).isEqualTo(expected.followedHighlight)
        assertThat(actual.hoveredStyle).`as`(description).isEqualTo(expected.hoveredHighlight)
        assertThat(actual.layer).`as`(description).isEqualTo(expectedLayer)
      }
    }

    suspend fun assertHighlightings(vararg expectedHighlightings: Highlighting) {
      awaitEventProcessing()
      val actualHighlightings = backendFacade.dumpState().hyperlinks.filterIsInstance<TerminalHighlightingInfo>()
      assertThat(actualHighlightings).hasSameSizeAs(expectedHighlightings)
      for (i in actualHighlightings.indices) {
        val actual = actualHighlightings[i]
        val expected = expectedHighlightings[i]
        val expectedStartOffset = expected.locator.locateOffset(document)
        val expectedEndOffset = expectedStartOffset + expected.locator.length
        val actualStartOffset = outputModel.absoluteOffset(actual.absoluteStartOffset).toRelative()
        val actualEndOffset = outputModel.absoluteOffset(actual.absoluteEndOffset).toRelative()
        val expectedLayer = HighlighterLayer.CONSOLE_FILTER

        val description = "at $i actual highlight $actual expected highlight $expected"
        assertThat(actualStartOffset).`as`(description).isEqualTo(expectedStartOffset)
        assertThat(actualEndOffset).`as`(description).isEqualTo(expectedEndOffset)
        assertThat(actual.style).`as`(description).isEqualTo(expected.highlight)
        assertThat(actual.layer).`as`(description).isEqualTo(expectedLayer)
      }
    }

    suspend fun assertInlays(vararg expectedInlays: Inlay) {
      awaitEventProcessing()
      val actualInlays = backendFacade.dumpState().hyperlinks.filterIsInstance<TerminalInlayInfo>()
      assertThat(actualInlays).hasSameSizeAs(expectedInlays)
      for (i in actualInlays.indices) {
        val actual = actualInlays[i]
        val expected = expectedInlays[i]
        val expectedStartOffset = expected.locator.locateOffset(document)
        val expectedEndOffset = expectedStartOffset + expected.locator.length
        val actualStartOffset = outputModel.absoluteOffset(actual.absoluteStartOffset).toRelative()
        val actualEndOffset = outputModel.absoluteOffset(actual.absoluteEndOffset).toRelative()

        val description = "at $i actual inlay $actual expected inlay $expected"
        assertThat(actualStartOffset).`as`(description).isEqualTo(expectedStartOffset)
        assertThat(actualEndOffset).`as`(description).isEqualTo(expectedEndOffset)
      }
    }

    suspend fun assertClicks(vararg clicks: LinkLocator) {
      awaitEventProcessing()
      for (click in clicks) {
        backendFacade.hyperlinkClicked(click.locateLink(outputModel, backendFacade).id, null)
      }
      awaitEventProcessing()
      assertThat(clickedLinks).containsExactlyElementsOf(clicks.map { it.substring })
    }

    fun link(
      at: LinkLocator,
      highlight: TextAttributes? = filter.highlight,
      followedHighlight: TextAttributes? = filter.followedHighlight,
      hoveredHighlight: TextAttributes? = filter.hoveredHighlight,
    ): Link = Link(at, highlight, followedHighlight, hoveredHighlight)

    fun inlay(
      at: LinkLocator,
    ): Inlay = Inlay(at)

    fun highlight(
      at: LinkLocator,
      highlight: TextAttributes? = filter.highlight,
    ): Highlighting = Highlighting(at, highlight)

    fun at(line: Int, substring: String): LinkLocator = LinkLocator(line, substring)

    private suspend fun awaitEventProcessing() {
      pendingUpdateEventCount.first { it == 0 }
      backendFacade.awaitTaskCompletion()
    }

    data class Link(
      val locator: LinkLocator,
      val highlight: TextAttributes?,
      val followedHighlight: TextAttributes?,
      val hoveredHighlight: TextAttributes?,
    )

    data class Highlighting(
      val locator: LinkLocator,
      val highlight: TextAttributes?,
    )

    data class Inlay(
      val locator: LinkLocator,
    )

    data class LinkLocator(val line: Int, val substring: String) {
      val length: Int get() = substring.length

      fun locateOffset(document: Document): Int {
        val lineStart = document.getLineStartOffset(line)
        val lineEnd = document.getLineEndOffset(line)
        val lineText = document.immutableCharSequence.substring(lineStart, lineEnd)
        val column = lineText.indexOfSingle(substring)
        return lineStart + column
      }

      fun locateLink(outputModel: TerminalOutputModel, backendFacade: BackendTerminalHyperlinkFacade): TerminalFilterResultInfo {
        val offset = outputModel.relativeOffset(locateOffset(outputModel.document))
        val links = backendFacade.dumpState().hyperlinks
        return links.single { offset.toAbsolute() in it.absoluteStartOffset until it.absoluteEndOffset }
      }
    }

    inner class MyFilter : Filter {
      var highlight: TextAttributes? = null
      var followedHighlight: TextAttributes? = null
      var hoveredHighlight: TextAttributes? = null
      var delayPerLine = 0L

      private val pattern = Regex("""(link|highlight|link_inlay|highlight_inlay)\d+""")

      override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        if (delayPerLine > 0) {
          Thread.sleep(delayPerLine)
        }
        val startOffset = entireLength - line.length
        val results = mutableListOf<Filter.ResultItem>()
        pattern.findAll(line).forEach { matchResult ->
          val isHyperlink = matchResult.groups[1]?.value?.startsWith("link") == true
          val isInlay = matchResult.groups[1]?.value?.endsWith("_inlay") == true
          results += createResultItem(
            highlightStartOffset = startOffset + matchResult.range.first,
            highlightEndOffset = startOffset + matchResult.range.last + 1,
            hyperlinkInfo = if (isHyperlink) MyHyperlinkInfo(matchResult.value) else null,
            highlightAttributes = highlight,
            followedHyperlinkAttributes = if (isHyperlink) followedHighlight else null,
            hoveredHyperlinkAttributes = if (isHyperlink) hoveredHighlight else null,
            isInlay = isInlay,
          )
        }
        return if (results.isNotEmpty()) Filter.Result(results) else null
      }

      private fun createResultItem(
        highlightStartOffset: Int,
        highlightEndOffset: Int,
        hyperlinkInfo: HyperlinkInfo?,
        highlightAttributes: TextAttributes?,
        followedHyperlinkAttributes: TextAttributes?,
        hoveredHyperlinkAttributes: TextAttributes?,
        isInlay: Boolean,
      ): Filter.ResultItem = if (isInlay) {
        InlayResultItem(
          highlightStartOffset,
          highlightEndOffset,
          hyperlinkInfo,
          highlightAttributes,
          followedHyperlinkAttributes,
          hoveredHyperlinkAttributes
        )
      }
      else {
        Filter.ResultItem(
          highlightStartOffset,
          highlightEndOffset,
          hyperlinkInfo,
          highlightAttributes,
          followedHyperlinkAttributes,
          hoveredHyperlinkAttributes
        )
      }
    }

    private inner class MyHyperlinkInfo(private val value: String) : HyperlinkInfo {
      override fun navigate(project: Project) {
        clickedLinks += value
      }
    }
  }
}

private class InlayResultItem(
  highlightStartOffset: Int,
  highlightEndOffset: Int,
  hyperlinkInfo: HyperlinkInfo?,
  highlightAttributes: TextAttributes?,
  followedHyperlinkAttributes: TextAttributes?,
  hoveredHyperlinkAttributes: TextAttributes?,
) : Filter.ResultItem(
  highlightStartOffset,
  highlightEndOffset,
  hyperlinkInfo,
  highlightAttributes,
  followedHyperlinkAttributes,
  hoveredHyperlinkAttributes
), InlayProvider

private val HIGHLIGHT1 =
  EditorColorsManager.getInstance().globalScheme.getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES)

private val HIGHLIGHT2 =
  EditorColorsManager.getInstance().globalScheme.getAttributes(CodeInsightColors.FOLLOWED_HYPERLINK_ATTRIBUTES)

private val HIGHLIGHT3 =
  EditorColorsManager.getInstance().globalScheme.getAttributes(CodeInsightColors.TODO_DEFAULT_ATTRIBUTES)

private val HIGHLIGHT4 =
  EditorColorsManager.getInstance().globalScheme.getAttributes(CodeInsightColors.BOOKMARKS_ATTRIBUTES)

private const val MAX_LENGTH = 10000 // filters are processed in 200-line chunks, this should be enough to have multiple chunks

private fun String.indexOfSingle(substring: String): Int {
  val indexOfFirst = indexOf(substring)
  val indexOfLast = lastIndexOf(substring)
  require(indexOfFirst != -1) { "Substring '$substring' not found in '$this'" }
  require(indexOfFirst == indexOfLast) { "There are several substrings from $indexOfFirst to $indexOfLast" }
  return indexOfFirst
}
