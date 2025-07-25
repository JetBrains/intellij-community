// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.backend

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.backend.hyperlinks.BackendTerminalHyperlinkFacade
import com.intellij.terminal.session.*
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.flow.*
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModelImpl
import org.jetbrains.plugins.terminal.block.reworked.updateContent
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.time.Duration.Companion.milliseconds

@RunWith(JUnit4::class)
internal class BackendTerminalHyperlinkHighlighterTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `no links`() = withHelper {
    updateModel(0L, """
      0: line1
      1: line2
    """.trimIndent())
    assertText("""
      0: line1
      1: line2
    """.trimIndent())
  }

  @Test
  fun `some links`() = withHelper {
    updateModel(0L, """
      0: line1 link1
      1: line2 link2
    """.trimIndent())
    assertText("""
      0: line1 link1
      1: line2 link2
    """.trimIndent())
    assertLinks(
      link(at(0, "link1")),
      link(at(1, "link2")),
    )
  }

  @Test
  fun `remove line before processing`() = withHelper {
    updateModel(0L, """
      0: line1 link1
      1: line2 link2
    """.trimIndent())
    updateModel(1L, "")
    assertText("0: line1 link1\n")
    assertLinks(
      link(at(0, "link1")),
    )
  }

  @Test
  fun `remove line after processing`() = withHelper {
    updateModel(0L, """
      0: line1 link1
      1: line2 link2
    """.trimIndent())
    assertLinks(
      link(at(0, "link1")),
      link(at(1, "link2")),
    )
    updateModel(1L, "")
    assertText("0: line1 link1\n")
    assertLinks(
      link(at(0, "link1")),
    )
  }

  @Test
  fun `one click`() = withHelper {
    updateModel(0L, """
      0: line1 link1
      1: line2 link2
    """.trimIndent())
    assertClicks(
      at(0, "link1"),
    )
  }

  @Test
  fun `several clicks`() = withHelper {
    updateModel(0L, """
      0: line1 link1
      1: line2 link2
    """.trimIndent())
    assertClicks(
      at(0, "link1"),
      at(1, "link2"),
    )
  }

  @Test
  fun `several links per line`() = withHelper {
    updateModel(0L, """
      0: line1 link11 link12
      1: line2 link2
    """.trimIndent())
    assertText("""
      0: line1 link11 link12
      1: line2 link2
    """.trimIndent())
    assertLinks(
      link(at(0, "link11")),
      link(at(0, "link12")),
      link(at(1, "link2")),
    )
  }

  @Test
  fun `some links with highlighting`() = withHelper {
    filter.highlight = HIGHLIGHT1
    filter.followedHighlight = HIGHLIGHT2
    filter.hoveredHighlight = HIGHLIGHT3
    updateModel(0L, """
      0: line1 link1
    """.trimIndent())
    assertLinks(
      link(at(0, "link1")),
    )
    filter.highlight = HIGHLIGHT2
    filter.followedHighlight = HIGHLIGHT3
    filter.hoveredHighlight = HIGHLIGHT4
    updateModel(1L, """
      1: line2 link2
    """.trimIndent())
    assertText("""
      0: line1 link1
      1: line2 link2
    """.trimIndent())
    assertLinks(
      link(at(0, "link1"), highlight = HIGHLIGHT1, followedHighlight = HIGHLIGHT2, hoveredHighlight = HIGHLIGHT3),
      link(at(1, "link2"), highlight = HIGHLIGHT2, followedHighlight = HIGHLIGHT3, hoveredHighlight = HIGHLIGHT4),
    )
  }

  @Test
  fun `some highlighting`() = withHelper {
    filter.highlight = HIGHLIGHT1
    updateModel(0L, """
      0: line1 highlight1
    """.trimIndent())
    assertHighlightings(
      highlight(at(0, "highlight1")),
    )
    filter.highlight = HIGHLIGHT2
    updateModel(1L, """
      1: line2 highlight2
    """.trimIndent())
    assertText("""
      0: line1 highlight1
      1: line2 highlight2
    """.trimIndent())
    assertHighlightings(
      highlight(at(0, "highlight1"), highlight = HIGHLIGHT1),
      highlight(at(1, "highlight2"), highlight = HIGHLIGHT2),
    )
  }

  @Test
  fun `links update`() = withHelper {
    updateModel(0L, """
      0: line1 link1
      1: line2 link2
    """.trimIndent())
    assertLinks(
      link(at(0, "link1")),
      link(at(1, "link2")),
    )
    updateModel(1L, """
      1: line3 link3
    """.trimIndent())
    assertText("""
      0: line1 link1
      1: line3 link3
    """.trimIndent())
    assertLinks(
      link(at(0, "link1")),
      link(at(1, "link3")),
    )
  }

  @Test
  fun `many links, fast filter`() = withHelper {
    updateModel(0L, generateLines(0, 499, links = (0..499).toList()))
    assertLinks(
      *(0..499).map { link(at(it, "link${it + 1}")) }.toTypedArray(),
    )
  }

  @Test
  fun `many links, slow filter, several updates`() = withHelper {
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
      *(0..499).map { link(at(it, "link${it + 1}")) }.toTypedArray(),
    )
  }

  @Test
  fun `sparse links, fast filter`() = withHelper {
    updateModel(0L, generateLines(0, 499, links = listOf(1, 100, 400)))
    assertLinks(
      link(at(1, "link2")),
      link(at(100, "link101")),
      link(at(400, "link401")),
    )
  }

  @Test
  fun `link trimming, fast filter`() {
    withHelper {
      updateModel(0L, generateLines(0, 499, links = (0..499).toList()))
      updateModel(500L, generateLines(500, 3999, links = (500..3999).toList()))
      assertLinks(
        *(0 until 667).map { link(at(it, "link${it + 3334}")) }.toTypedArray(),
      )
    }
  }

  @Test
  fun `link trimming, slow filter, just started`() {
    withHelper {
      filter.delayPerLine = 1
      updateModel(0L, generateLines(0, 499, links = (0..499).toList()))
      delay(100.milliseconds)
      // now the filter is still in progress
      updateModel(500L, generateLines(500, 3999, links = (500..3999).toList()))
      assertLinks(
        *(0 until 667).map { link(at(it, "link${it + 3334}")) }.toTypedArray(),
      )
    }
  }

  @Test
  fun `link trimming, slow filter, partially done`() {
    withHelper {
      filter.delayPerLine = 1
      updateModel(0L, generateLines(0, 499, links = (0..499).toList()))
      delay(400.milliseconds)
      // now the filter is still in progress, but should have emitted some links
      updateModel(500L, generateLines(500, 3999, links = (500..3999).toList()))
      assertLinks(
        *(0 until 667).map { link(at(it, "link${it + 3334}")) }.toTypedArray(),
      )
    }
  }

  private fun generateLines(from: Int, toInclusive: Int, links: List<Int>): String {
    val linksAt = links.toSet()
    return (from..toInclusive).joinToString("\n") { line ->
      "$line: ${if (line in linksAt) "link" else "line"}${line + 1}"
    }
  }

  private fun withHelper(test: suspend Helper.() -> Unit) = timeoutRunBlocking(coroutineName = "BackendTerminalHyperlinkHighlighterTest") {
    val helper = Helper(project)
    ExtensionTestUtil.maskExtensions<ConsoleFilterProvider>(
      ConsoleFilterProvider.FILTER_PROVIDERS,
      listOf(ConsoleFilterProvider { arrayOf(helper.filter as Filter) }),
      testRootDisposable
    )
    withContext(Dispatchers.Default) {
      helper.run(test)
    }
  }

  private class Helper(private val project: Project) {

    private val document = DocumentImpl("", true)
    private val outputModel = TerminalOutputModelImpl(document, MAX_LENGTH)
    private lateinit var backendFacade: BackendTerminalHyperlinkFacade
    private val updateEvents = MutableSharedFlow<List<TerminalOutputEvent>>(replay = 100)
    private val pendingUpdateEventCount = MutableStateFlow(0)

    val filter = MyFilter()

    private val clickedLinks = mutableListOf<String>()

    suspend fun run(test: suspend Helper.() -> Unit) {
      coroutineScope {
        val hyperlinkScope = childScope("BackendTerminalHyperlinkHighlighterTest hyperlink scope")
        backendFacade = BackendTerminalHyperlinkFacade(project, hyperlinkScope, outputModel, false)

        // do what StateAwareTerminalSession does, but with less infrastructure around
        val eventJob = launch(CoroutineName("BackendTerminalHyperlinkHighlighterTest event processing"), start = UNDISPATCHED) {
          merge(updateEvents, backendFacade.resultFlow).collect { events ->
            events.forEach { event ->
              when (event) {
                is TerminalContentUpdatedEvent -> {
                  outputModel.updateContent(event)
                  pendingUpdateEventCount.update { it - 1 }
                }
                is TerminalHyperlinksChangedEvent -> {
                  backendFacade.updateModelState(event)
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
        val expectedStartOffset = expected.locator.locateOffset(document)
        val expectedEndOffset = expectedStartOffset + expected.locator.length
        val actualStartOffset = outputModel.absoluteOffset(actual.absoluteStartOffset).toRelative()
        val actualEndOffset = outputModel.absoluteOffset(actual.absoluteEndOffset).toRelative()
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

    suspend fun assertClicks(vararg clicks: LinkLocator) {
      awaitEventProcessing()
      for (click in clicks) {
        backendFacade.hyperlinkClicked(click.locateLink(outputModel, backendFacade).id)
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

    data class LinkLocator(val line: Int, val substring: String) {
      val length: Int get() = substring.length

      fun locateOffset(document: DocumentImpl): Int {
        val lineStart = document.getLineStartOffset(line)
        val lineEnd = document.getLineEndOffset(line)
        val lineText = document.immutableCharSequence.substring(lineStart, lineEnd)
        val column = lineText.indexOfSingle(substring)
        return lineStart + column
      }

      fun locateLink(outputModel: TerminalOutputModel, backendFacade: BackendTerminalHyperlinkFacade): TerminalFilterResultInfo {
        val offset = outputModel.relativeOffset(locateOffset(outputModel.document as DocumentImpl))
        val links = backendFacade.dumpState().hyperlinks
        return links.single { offset.toAbsolute() in it.absoluteStartOffset until it.absoluteEndOffset }
      }
    }

    inner class MyFilter : Filter {
      var highlight: TextAttributes? = null
      var followedHighlight: TextAttributes? = null
      var hoveredHighlight: TextAttributes? = null
      var delayPerLine = 0L

      private val pattern = Regex("""(link|highlight)\d+""")

      override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        if (delayPerLine > 0) {
          Thread.sleep(delayPerLine)
        }
        val startOffset = entireLength - line.length
        val results = mutableListOf<Filter.ResultItem>()
        pattern.findAll(line).forEach { matchResult ->
          val isHyperlink = matchResult.groups[1]?.value == "link"
          results += Filter.ResultItem(
            startOffset + matchResult.range.first,
            startOffset + matchResult.range.last + 1,
            if (isHyperlink) MyHyperlinkInfo(matchResult.value) else null,
            highlight,
            if (isHyperlink) followedHighlight else null,
            if (isHyperlink) hoveredHighlight else null,
          )
        }
        return if (results.isNotEmpty()) Filter.Result(results) else null
      }
    }

    private inner class MyHyperlinkInfo(private val value: String) : HyperlinkInfo {
      override fun navigate(project: Project) {
        clickedLinks += value
      }
    }
  }
}

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
