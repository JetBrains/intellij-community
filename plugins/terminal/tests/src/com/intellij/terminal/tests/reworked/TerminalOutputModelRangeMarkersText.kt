// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.util.registry.Registry
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.text
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModelImpl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Tests that [RangeMarker]'s of the [org.jetbrains.plugins.terminal.view.TerminalOutputModel] document
 * survive content updates.
 */
@RunWith(JUnit4::class)
internal class TerminalOutputModelRangeMarkersText : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `scroll up (new lines at the bottom) keeps markers over the shifted block`() = withFixture {
    // 20 lines on screen.
    update(0, lines(0..19))
    val marker = createMarkerOverText("line10")

    // Scroll up by one line: the top line disappears, a new line appears at the bottom.
    update(0, lines(1..20))

    assertThat(text).isEqualTo(lines(1..20))
    assertMarkerSurvived(marker, "line10")
  }

  @Test
  fun `scroll down (new lines at the top) keeps markers over the shifted block`() = withFixture {
    update(0, lines(1..20))
    val marker = createMarkerOverText("line10")

    // Scroll down by one line: the bottom line disappears, a new line appears at the top.
    update(0, lines(0..19))

    assertThat(text).isEqualTo(lines(0..19))
    assertMarkerSurvived(marker, "line10")
  }

  @Test
  fun `scroll by several lines keeps markers over the shifted block`() = withFixture {
    update(0, lines(0..19))
    val marker = createMarkerOverText("line10")

    // Scroll up by three lines.
    update(0, lines(3..22))

    assertThat(text).isEqualTo(lines(3..22))
    assertMarkerSurvived(marker, "line10")
  }

  @Test
  fun `scroll through highly repetitive content keeps every preserved line's marker in place`() = withFixture {
    // A stack trace printed twice on screen: consecutive scroll frames share a period-aligned block, which the
    // shared-block detection must not mistake for the actual (nearly whole-screen) shift.
    val block = listOf(
      "Exception in thread \"main\" java.lang.ArithmeticException: / by zero",
      "\tat Test1.d(Test1.java:26)",
      "\tat Test1.c(Test1.java:22)",
      "\tat Test1.b(Test1.java:18)",
      "\tat Test1.a(Test1.java:14)",
      "\tat Test1.main(Test1.java:9)",
      "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)",
      "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)",
      "\tat java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)",
      "\tat java.base/java.lang.reflect.Method.invoke(Method.java:568)",
      "\tat com.intellij.rt.execution.application.AppMainV2.main(AppMainV2.java:133)",
    )
    val all = (0 until 8).flatMap { block } // 8 identical copies => highly repetitive
    val height = 21
    val start = 33

    update(0, all.subList(start, start + height).joinToString("\n"))
    val markers = (0 until height).map { line ->
      document.createRangeMarker(document.getLineStartOffset(line), document.getLineEndOffset(line))
    }
    // Scroll up by one line: a new line enters at the top, the bottom line leaves.
    update(0, all.subList(start - 1, start - 1 + height).joinToString("\n"))

    // Every line except the one that scrolled off must keep its marker, moved down by exactly one line —
    // asserted by offset, so a marker landing on a different (identical) repeated line still fails.
    for (i in 0 until height - 1) {
      assertThat(markers[i].isValid).`as`("marker for line $i must survive the scroll").isTrue()
      assertThat(markers[i].startOffset).`as`("marker for line $i start").isEqualTo(document.getLineStartOffset(i + 1))
      assertThat(markers[i].endOffset).`as`("marker for line $i end").isEqualTo(document.getLineEndOffset(i + 1))
    }
  }

  @Test
  fun `scroll preserves the shifted block when a fixed blank line trails the content`() = withFixture {
    // Reproduces a real vim scroll: a repeated stack trace with a fixed trailing blank line.
    // The blank line stays put at the bottom while the content above it scrolls, so the shifted block no longer
    // reaches the end of the text — the detection must trim the common trailing line first.
    val block = listOf(
      "Exception in thread \"main\" java.lang.ArithmeticException: / by zero",
      "\tat Test1.d(Test1.java:26)",
      "\tat Test1.c(Test1.java:22)",
      "\tat Test1.b(Test1.java:18)",
      "\tat Test1.a(Test1.java:14)",
      "\tat Test1.main(Test1.java:9)",
      "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)",
      "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)",
      "\tat java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)",
      "\tat java.base/java.lang.reflect.Method.invoke(Method.java:568)",
      "\tat com.intellij.rt.execution.application.AppMainV2.main(AppMainV2.java:133)",
    )
    val content = (0 until 8).flatMap { block } // 8 identical copies => highly repetitive
    val contentHeight = 24
    val blankLine = "                    " // a fixed blank line trailing the screen
    val start = 33

    fun screen(top: Int) = (content.subList(top, top + contentHeight) + blankLine).joinToString("\n")

    update(0, screen(start))
    val markers = (0..contentHeight).map { line ->
      document.createRangeMarker(document.getLineStartOffset(line), document.getLineEndOffset(line))
    }
    // Scroll the content up by one line; the trailing blank line stays fixed at the bottom.
    update(0, screen(start + 1))

    // Content lines 1..contentHeight-1 shift up by one and keep their markers (asserted by offset).
    for (i in 1 until contentHeight) {
      assertThat(markers[i].isValid).`as`("marker for content line $i must survive").isTrue()
      assertThat(markers[i].startOffset).`as`("marker $i start").isEqualTo(document.getLineStartOffset(i - 1))
      assertThat(markers[i].endOffset).`as`("marker $i end").isEqualTo(document.getLineEndOffset(i - 1))
    }
    // The fixed trailing blank line keeps its marker too.
    assertThat(markers[contentHeight].isValid).`as`("blank line marker must survive").isTrue()
    assertThat(markers[contentHeight].startOffset).isEqualTo(document.getLineStartOffset(contentHeight))
  }

  @Test
  fun `scroll up by one line preserves hyperlink lines despite a differing trailing blank line`() = withFixture {
    // Reproduces the real vim scroll: the whole screen shifts up by one content line, but the
    // trailing blank line has a different length in old vs new (terminal repadding), so a prefix/suffix or border
    // heuristic fails and the whole screen would be replaced. A real line diff must still preserve the shifted lines.
    val block = listOf(
      "        at com.intellij.rt.execution.application.AppMainV2.main(AppMainV2.java:133)",
      "Exception in thread \"main\" java.lang.ArithmeticException: / by zero",
      "        at Test1.d(Test1.java:26)",
      "        at Test1.c(Test1.java:22)",
      "        at Test1.b(Test1.java:18)",
      "        at Test1.a(Test1.java:14)",
      "        at Test1.main(Test1.java:9)",
      "        at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)",
      "        at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)",
      "        at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)",
      "        at java.base/java.lang.reflect.Method.invoke(Method.java:568)",
    )
    val content = (0 until 8).flatMap { block }
    val contentHeight = 19
    val start = 40
    val old = content.subList(start, start + contentHeight) + " ".repeat(120)
    val new = content.subList(start + 1, start + 1 + contentHeight) + " ".repeat(110)

    update(0, old.joinToString("\n"))
    val markers = old.indices.map { document.createRangeMarker(document.getLineStartOffset(it), document.getLineEndOffset(it)) }
    update(0, new.joinToString("\n"))

    assertThat(text).isEqualTo(new.joinToString("\n"))
    // old content lines 1..contentHeight-1 are identical to new lines 0..contentHeight-2 => markers must survive.
    for (oldLine in 1 until contentHeight) {
      val newLine = oldLine - 1
      assertThat(markers[oldLine].isValid).`as`("hyperlink line $oldLine must survive").isTrue()
      assertThat(markers[oldLine].startOffset).`as`("line $oldLine start").isEqualTo(document.getLineStartOffset(newLine))
      assertThat(markers[oldLine].endOffset).`as`("line $oldLine end").isEqualTo(document.getLineEndOffset(newLine))
    }
  }

  @Test
  fun `changing one line preserves markers of all other lines`() = withFixture {
    update(0, (0..9).joinToString("\n") { "line$it value$it" })
    val markers = (0..9).map { document.createRangeMarker(document.getLineStartOffset(it), document.getLineEndOffset(it)) }

    update(0, (0..9).joinToString("\n") { if (it == 5) "line5 CHANGED" else "line$it value$it" })

    for (i in 0..9) {
      if (i == 5) continue
      assertThat(markers[i].isValid).`as`("line $i must survive").isTrue()
      assertThat(document.text.substring(markers[i].startOffset, markers[i].endOffset)).isEqualTo("line$i value$i")
    }
  }

  @Test
  fun `multiple changed regions produce correct text and preserve unchanged lines`() = withFixture {
    update(0, (0..9).joinToString("\n") { "line$it" })
    val markers = (0..9).map { document.createRangeMarker(document.getLineStartOffset(it), document.getLineEndOffset(it)) }

    val expected = (0..9).joinToString("\n") { if (it == 2 || it == 7) "changed$it" else "line$it" }
    update(0, expected)

    assertThat(text).isEqualTo(expected)
    for (i in listOf(0, 1, 3, 4, 5, 6, 8, 9)) {
      assertThat(markers[i].isValid).`as`("line $i must survive").isTrue()
      assertThat(document.text.substring(markers[i].startOffset, markers[i].endOffset)).isEqualTo("line$i")
    }
  }

  @Test
  fun `changing only the tail of a line keeps a marker over the unchanged head`() = withFixture {
    // a status line whose trailing counter changes ("... ; 2" -> "... ; 3").
    // The hyperlink over the URL (the unchanged head of the line) must not be wiped, otherwise it blinks until the async recompute.
    update(0, "Fetching http://example.com/pkg ; 2\nstatic line")
    val marker = createMarkerOverText("http://example.com/pkg")

    update(0, "Fetching http://example.com/pkg ; 3\nstatic line")

    assertMarkerSurvived(marker, "http://example.com/pkg")
  }

  @Test
  fun `changing only the head of a line keeps a marker over the unchanged tail`() = withFixture {
    update(0, "12% building http://example.com/pkg\nstatic line")
    val marker = createMarkerOverText("http://example.com/pkg")

    update(0, "34% building http://example.com/pkg\nstatic line")

    assertMarkerSurvived(marker, "http://example.com/pkg")
  }

  @Test
  fun `status lines with changing counters keep the markers over their unchanged URLs`() = withFixture {
    fun screen(counters: List<Int>) = counters.withIndex().joinToString("\n") { (i, c) ->
      "    Fetching https://example.com/dep$i.nupkg; $c"
    }
    update(0, screen(listOf(1, 2, 3, 4)))
    val markers = (0..3).map { createMarkerOverText("https://example.com/dep$it.nupkg") }

    update(0, screen(listOf(5, 6, 7, 8)))

    for (i in 0..3) {
      assertMarkerSurvived(markers[i], "https://example.com/dep$i.nupkg")
    }
  }

  @Test
  fun `an inserted status line keeps text correct and preserves markers on unchanged URL lines`() = withFixture {
    update(0, "Analyzing: 100 targets\nFetching http://a ; 1\nFetching http://b ; 2")
    val ma = createMarkerOverText("http://a")
    val mb = createMarkerOverText("http://b")

    val new = "Analyzing: 200 targets\ncurrently loading: x\nFetching http://a ; 1\nFetching http://b ; 2"
    update(0, new)

    assertThat(text).isEqualTo(new)
    assertMarkerSurvived(ma, "http://a")
    assertMarkerSurvived(mb, "http://b")
  }

  @Test
  fun `a URL line whose counter changes next to a removed line still produces correct text`() = withFixture {
    // Known limitation: when a URL line's counter changes AND an adjacent line is
    // removed/inserted, the URL lands in an unequal-line-count gap that is replaced as one edit, so its marker is
    // briefly wiped and re-added by the async recompute (a rare, transient blink).
    update(0, "Fetching http://a ; 1\nFetching http://b ; 5\nstatic")
    update(0, "Fetching http://b ; 9\nstatic")
    assertThat(text).isEqualTo("Fetching http://b ; 9\nstatic")
  }

  @Test
  fun `an update larger than the screen is a single replace and is not diffed`() = withFixture {
    // Performance guard: the line diff only runs for updates that fit the visible screen (the only place a wiped
    // marker would be seen). A bigger update — here a scroll of more than VISIBLE_SCREEN_LINES lines — is replaced in
    // one edit, so a marker over a shifted line is not preserved (whereas an in-screen scroll would preserve it).
    val height = (MutableTerminalOutputModelImpl.VISIBLE_SCREEN_LINES * 1.5).toInt()
    update(0, (0 until height).joinToString("\n") { "line$it" })
    val marker = createMarkerOverText("line75")

    update(0, (1..height).joinToString("\n") { "line$it" }) // scroll up by one, still 150 lines

    assertThat(text).isEqualTo((1..height).joinToString("\n") { "line$it" })
    assertThat(marker.isValid).`as`("a larger-than-screen update is a single replace, so the marker is wiped").isFalse()
  }

  @Test
  fun `with minimal edits disabled the whole changed range is replaced and markers are wiped`() {
    Registry.get("terminal.minimal.document.edits").setValue(false, testRootDisposable)
    withFixture {
      update(0, lines(0..19))
      val marker = createMarkerOverText("line10")

      // The same in-screen scroll that preserves the marker when the diff is on.
      update(0, lines(1..20))

      assertThat(text).isEqualTo(lines(1..20))
      assertThat(marker.isValid).`as`("with the diff off the range is replaced in one edit, wiping the marker").isFalse()
    }
  }

  private fun withFixture(block: suspend Fixture.() -> Unit) = runBlocking(Dispatchers.EDT) {
    Fixture(TerminalTestUtil.createOutputModel()).block()
  }

  private class Fixture(private val model: MutableTerminalOutputModelImpl) {
    val text: String get() = model.text

    val document get() = model.document

    fun update(absoluteLineIndex: Long, text: String) = model.updateContent(absoluteLineIndex, text, emptyList())

    /** Renders lines "line{i}", one per line, with a trailing newline. */
    fun lines(range: IntRange): String = range.joinToString(separator = "\n", postfix = "\n") { "line$it" }

    fun createMarkerOverText(lineText: String): RangeMarker {
      val start = model.document.text.indexOf(lineText)
      require(start >= 0) { "'$lineText' not found in the document" }
      val marker = model.document.createRangeMarker(start, start + lineText.length)
      assertThat(marker.isValid).isTrue()
      assertThat(model.markerText(marker)).isEqualTo(lineText)
      return marker
    }

    fun assertMarkerSurvived(marker: RangeMarker, expectedText: String) {
      assertThat(marker.isValid).`as`("marker should stay valid across the scroll").isTrue()
      assertThat(model.markerText(marker)).`as`("marker should still cover the same text").isEqualTo(expectedText)
    }

    private fun MutableTerminalOutputModelImpl.markerText(marker: RangeMarker): String {
      return document.text.substring(marker.startOffset, marker.endOffset)
    }
  }
}
