// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.hyperlinks

import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.terminal.frontend.view.hyperlinks.HYPERLINKS_OUTPUT_MODEL_FLUSH_DELAY
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import kotlinx.coroutines.delay
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@RunWith(JUnit4::class)
internal class TerminalHyperlinksProcessingTest : TerminalHyperlinksProcessingTestBase() {

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
  fun `async hyperlink uses async navigation`() = withFixture {
    filter.hyperlinkInfoFactory = { hyperlinkInfo ->
      createRecordingAsyncHyperlink(hyperlinkInfo, "async-only", failOnSyncNavigate = true)
    }
    updateModel(0L, "0: line0 link0")

    click(at(0, "link0"))

    assertClickedLinks("async-only")
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
      link(at(0, "link0"), highlight = HIGHLIGHT1),
      link(at(1, "link1"), highlight = HIGHLIGHT2),
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
  fun `many small updates, fast filter`() = withFixture {
    for (line in 0L..499L) {
      updateModel(line, "$line: link$line")
    }
    assertLinks(
      *(0..499).map { link(at(it, "link${it}")) }.toTypedArray(),
    )
  }

  @Test
  fun `many links, slow filter, several updates`() = withFixture(timeout = SLOW_FILTER_TEST_TIMEOUT) {
    filter.delayPerLine = 1
    updateModel(0L, generateLines(0, 499, links = (0..499).toList()))
    delay(OUTPUT_MODEL_FLUSH_AWAIT_DELAY)
    updateModel(100L, generateLines(100, 199, links = (100..199).toList()))
    delay(OUTPUT_MODEL_FLUSH_AWAIT_DELAY)
    updateModel(200L, generateLines(200, 299, links = (200..299).toList()))
    delay(OUTPUT_MODEL_FLUSH_AWAIT_DELAY)
    updateModel(300L, generateLines(300, 399, links = (300..399).toList()))
    delay(OUTPUT_MODEL_FLUSH_AWAIT_DELAY)
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
    withFixture(timeout = SLOW_FILTER_TEST_TIMEOUT) {
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
    withFixture(timeout = SLOW_FILTER_TEST_TIMEOUT) {
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
          (1 until 764).map { link(at(it, "link${it + 234}")) } +
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

  @Test
  fun `separate submissions across the flush interval`() = withFixture {
    updateModel(0L, generateLines(0, 2, links = (0..2).toList()))
    delay(OUTPUT_MODEL_FLUSH_AWAIT_DELAY)
    updateModel(3L, generateLines(3, 5, links = (3..5).toList()))
    assertText(generateLines(0, 5, links = (0..5).toList()))
    assertLinks(
      *(0..5).map { link(at(it, "link$it")) }.toTypedArray(),
    )
    assertHighlightings()
  }

  @Test
  fun `coalesced submissions within the flush interval`() = withFixture {
    // No suspension between the updates: both land in a single flush interval.
    updateModel(0L, generateLines(0, 2, links = (0..2).toList()))
    updateModel(3L, generateLines(3, 5, links = (3..5).toList()))
    assertText(generateLines(0, 5, links = (0..5).toList()))
    assertLinks(
      *(0..5).map { link(at(it, "link$it")) }.toTypedArray(),
    )
    assertHighlightings()
  }

  @Test
  fun `re-edit a line across flush intervals`() = withFixture {
    updateModel(0L, """
      0: line0 link0
      1: line1 link1
    """.trimIndent())
    delay(OUTPUT_MODEL_FLUSH_AWAIT_DELAY)
    updateModel(1L, "1: line1 link5")
    assertText("""
      0: line0 link0
      1: line1 link5
    """.trimIndent())
    assertLinks(
      link(at(0, "link0")),
      link(at(1, "link5")),
    )
    assertHighlightings()
  }

  @Test
  fun `repeated edits of the same line across flushes`() = withFixture {
    for (i in 0..9) {
      updateModel(0L, "0: line0 link$i")
      delay(30.milliseconds)
    }
    assertText("0: line0 link9")
    assertLinks(
      link(at(0, "link9")),
    )
    assertHighlightings()
  }

  @Test
  fun `link removed while filter is mid-task`() = withFixture {
    filter.delayPerLine = 1
    updateModel(0L, generateLines(0, 199, links = (0..199).toList()))
    delay(OUTPUT_MODEL_FLUSH_AWAIT_DELAY) // the first task is still running
    // Resend lines 100..199 with line 100 turned into a plain line.
    updateModel(100L, generateLines(100, 199, links = (101..199).toList()))
    assertLinks(
      *((0..99) + (101..199)).map { link(at(it, "link$it")) }.toTypedArray(),
    )
    assertHighlightings()
  }

  @Test
  fun `link replaced while filter is mid-task`() = withFixture {
    filter.delayPerLine = 1
    updateModel(0L, generateLines(0, 199, links = (0..199).toList()))
    delay(OUTPUT_MODEL_FLUSH_AWAIT_DELAY)
    // Resend lines 100..199 with the link on line 100 changed to a different one.
    val rewritten = (100..199).joinToString("\n") { line ->
      if (line == 100) "100: link999" else "$line: link$line"
    }
    updateModel(100L, rewritten)
    assertLinks(
      *((0..99).map { link(at(it, "link$it")) } +
        link(at(100, "link999")) +
        (101..199).map { link(at(it, "link$it")) }).toTypedArray(),
    )
    assertHighlightings()
  }

  @Test
  fun `link offset recomputed when a line changes length mid-task`() = withFixture {
    filter.delayPerLine = 1
    updateModel(0L, generateLines(0, 199, links = (0..199).toList()))
    delay(OUTPUT_MODEL_FLUSH_AWAIT_DELAY)
    // Resend lines 100..199, widening line 100 so the link on it and all following offsets shift.
    val rewritten = (100..199).joinToString("\n") { line ->
      if (line == 100) "100: xxxxx link100" else "$line: link$line"
    }
    updateModel(100L, rewritten)
    assertLinks(
      *(0..199).map { link(at(it, "link$it")) }.toTypedArray(),
    )
    assertHighlightings()
  }

  @Test
  fun `link removed then restored across mid-task edits`() = withFixture {
    filter.delayPerLine = 1
    updateModel(0L, generateLines(0, 199, links = (0..199).toList()))
    delay(OUTPUT_MODEL_FLUSH_AWAIT_DELAY)
    updateModel(100L, generateLines(100, 199, links = (101..199).toList())) // line 100 plain
    delay(OUTPUT_MODEL_FLUSH_AWAIT_DELAY)
    updateModel(100L, generateLines(100, 199, links = (100..199).toList())) // line 100 link again
    assertLinks(
      *(0..199).map { link(at(it, "link$it")) }.toTypedArray(),
    )
    assertHighlightings()
  }

  @Test
  fun `multiple overlapping updates while filter is mid-task`() = withFixture {
    filter.delayPerLine = 1
    updateModel(0L, generateLines(0, 299, links = (0..299).toList()))
    delay(OUTPUT_MODEL_FLUSH_AWAIT_DELAY)
    updateModel(100L, generateLines(100, 299, links = (100..299).toList()))
    delay(20.milliseconds)
    updateModel(200L, generateLines(200, 299, links = (200..299).toList()))
    assertLinks(
      *(0..299).map { link(at(it, "link$it")) }.toTypedArray(),
    )
    assertHighlightings()
  }

  @Test
  fun `scroll up keeps links aligned`() = withFixture {
    // An alternate-buffer redraw arrives as a whole-screen update from line 0.
    updateModel(0L, generateLines(200, 219, links = (200..219).toList()))
    assertLinks(*(200..219).map { link(at(it - 200, "link$it")) }.toTypedArray())
    // Scroll up by one line.
    updateModel(0L, generateLines(201, 220, links = (201..220).toList()))
    assertLinks(*(201..220).map { link(at(it - 201, "link$it")) }.toTypedArray())
  }

  @Test
  fun `rapid scrolls with a slow filter keep links aligned`() = withFixture {
    filter.delayPerLine = 1
    updateModel(0L, generateLines(200, 219, links = (200..219).toList()))
    for (top in 201..210) {
      updateModel(0L, generateLines(top, top + 19, links = (top..top + 19).toList()))
      delay(5.milliseconds)
    }
    assertLinks(*(210..229).map { link(at(it - 210, "link$it")) }.toTypedArray())
  }

  @Test
  fun `screen cleared while the filter is mid-task leaves no stale links`() = withFixture {
    filter.delayPerLine = 1
    // A long, fully-linked screen begins processing across several batches (slow filter).
    updateModel(0L, generateLines(0, 299, links = (0..299).toList()))
    delay(OUTPUT_MODEL_FLUSH_AWAIT_DELAY) // the task is still running; only part of the screen is linkified
    // The whole screen is cleared down to a single, different line (e.g. `clear`).
    updateModel(0L, "0: cleared link500")
    assertText("0: cleared link500")
    assertLinks(
      link(at(0, "link500")),
    )
    assertHighlightings()
  }

  private fun generateLines(from: Int, toInclusive: Int, links: List<Int>): String {
    val linksAt = links.toSet()
    return (from..toInclusive).joinToString("\n") { line ->
      "$line: ${if (line in linksAt) "link" else "line"}${line}"
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

private val OUTPUT_MODEL_FLUSH_AWAIT_DELAY: Duration = HYPERLINKS_OUTPUT_MODEL_FLUSH_DELAY * 2

// The test's fake filter simulates slowness with Thread.sleep(delayPerLine) on every line, thousands of times for the
// largest scenarios here. Windows' OS timer resolution (~15.6 ms) can round each 1 ms sleep up by an order of
// magnitude, especially on a loaded/virtualized CI agent, so DEFAULT_TEST_TIMEOUT (10s) isn't enough headroom.
private val SLOW_FILTER_TEST_TIMEOUT: Duration = DEFAULT_TEST_TIMEOUT * 6
