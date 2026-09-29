// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend

import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.terminal.tests.reworked.util.TerminalViewFixture
import com.intellij.terminal.tests.reworked.util.TerminalViewTestCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.junit.jupiter.api.Test
import java.awt.event.MouseEvent
import kotlin.time.Duration.Companion.milliseconds

/**
 * End-to-end coverage of OSC8 hyperlinks: a real [com.intellij.terminal.frontend.view.impl.TerminalViewImpl] is
 * connected to the production `TerminalSession`, backed by a loopback connector instead of a real shell process.
 * Raw `OSC 8` escape sequences are fed through the connector, and the final state is asserted where the UI
 * actually renders it: a hyperlink [RangeHighlighter] in the markup model of the active buffer's editor.
 *
 * Every case runs on both JediTerm and Ghostty emulators.
 */
internal class TerminalOsc8HyperlinksEndToEndTest(emulatorType: TerminalEmulatorType) : TerminalViewTestCase(emulatorType) {

  @Test
  fun `OSC8 hyperlink text can differ from its target URI`() = doTest { fixture ->
    fixture.connector.feed("before ${osc8("https://example.com", "link text")} after")

    val highlighter = fixture.awaitHyperlink()
    assertThat(fixture.textOf(highlighter)).isEqualTo("link text")
    assertThat(fixture.uriOf(highlighter)).isEqualTo("https://example.com")
  }

  @Test
  fun `several OSC8 hyperlinks in the same output are each rendered separately`() = doTest { fixture ->
    fixture.connector.feed("${osc8("https://jetbrains.com", "FIRST")} middle ${osc8("https://example.com", "SECOND")}")

    val highlighters = fixture.awaitHyperlinks(2)
    assertThat(highlighters.map { fixture.textOf(it) }).containsExactly("FIRST", "SECOND")
    assertThat(highlighters.map { fixture.uriOf(it) }).containsExactly("https://jetbrains.com", "https://example.com")
  }

  @Test
  fun `two adjacent OSC8 writes with the same URI are rendered as a single hyperlink`() = doTest { fixture ->
    fixture.connector.feed(osc8("https://jetbrains.com", "foo") + osc8("https://jetbrains.com", "bar"))

    val highlighter = fixture.awaitHyperlink()
    assertThat(fixture.textOf(highlighter)).isEqualTo("foobar")
    assertThat(fixture.uriOf(highlighter)).isEqualTo("https://jetbrains.com")
  }

  @Test
  fun `hyperlink split into several ranges by an in-place edit is still rendered as a single hyperlink`() = doTest { fixture ->
    fixture.connector.feed(osc8("https://jetbrains.com", "world"))
    fixture.connector.feed("${ESC}[3D") // move the cursor back to the "r" in "world"
    fixture.connector.feed(osc8("https://jetbrains.com", "R"))

    val highlighter = fixture.awaitHyperlink()
    assertThat(fixture.textOf(highlighter)).isEqualTo("woRld")
    assertThat(fixture.uriOf(highlighter)).isEqualTo("https://jetbrains.com")
  }

  @Test
  fun `adjacent OSC8 hyperlinks with different URIs are not collapsed into one`() = doTest { fixture ->
    fixture.connector.feed(osc8("https://jetbrains.com", "foo") + osc8("https://example.com", "bar"))

    val highlighters = fixture.awaitHyperlinks(2)
    assertThat(highlighters.map { fixture.textOf(it) }).containsExactly("foo", "bar")
    assertThat(highlighters.map { fixture.uriOf(it) }).containsExactly("https://jetbrains.com", "https://example.com")
  }

  @Test
  fun `a target that is not a recognized URL is not rendered as a hyperlink`() = doTest { fixture ->
    fixture.connector.feed(osc8("definitely-not-a-url", "click me"))
    // A real link fed right after: once it's rendered, reconciliation has scanned the whole link list
    // (including the one above) at least once, so a missing highlighter for "click me" isn't just a timing fluke.
    fixture.connector.feed(osc8("https://jetbrains.com", "SENTINEL"))

    val highlighters = fixture.awaitHyperlinks(1)
    assertThat(fixture.textOf(highlighters.single())).isEqualTo("SENTINEL")
  }

  @Test
  fun `hyperlink is removed from the markup model once its line is overwritten with plain text`() = doTest { fixture ->
    fixture.connector.feed(osc8("https://jetbrains.com", "LINK"))
    fixture.awaitHyperlink()

    // Move the cursor back to the start of the line and overwrite it with plain text at least as long as "LINK".
    fixture.connector.feed("\rplain text, no links here")

    fixture.awaitHyperlinks(0)
  }

  @Test
  fun `hovering an OSC8 hyperlink shows its target URI as a tooltip`() = doTest { fixture ->
    // Give the output editor a real pixel size, so hover() below can convert an offset to a valid screen point.
    fixture.resize(columns = 80, rows = 24)
    fixture.connector.feed("x ${osc8("https://jetbrains.com", "JB")} y")

    val highlighter = fixture.awaitHyperlink()
    fixture.hover(highlighter)

    // The link text ("JB") says nothing about the destination - the tooltip is what reveals it.
    assertThat(fixture.view.outputEditor.contentComponent.toolTipText).contains("https://jetbrains.com")
  }

  @Test
  fun `a link spanning a soft-wrapped line stays one range`() = doTest { fixture ->
    val uri = "https://example.com/wrapped"
    // 70 plain chars, then 20 linked chars: the link crosses the 80-column boundary into the next row.
    val linkText = "L".repeat(20)
    fixture.connector.feed("X".repeat(70) + osc8(uri, linkText) + "END")

    val highlighter = fixture.awaitHyperlink()
    // Wrapped rows join without a '\n', so the linked text is contiguous in the rendered document.
    assertThat(fixture.textOf(highlighter)).isEqualTo(linkText)
    assertThat(fixture.uriOf(highlighter)).isEqualTo(uri)
  }

  @Test
  fun `a URL split over two lines is merged into one hyperlink`() = doTest { fixture ->
    val uri = "https://example.com/two-lines"
    fixture.connector.feed(osc8(uri, "https://example.com/") + "\r\n" + osc8(uri, "two-lines"))

    val highlighter = fixture.awaitHyperlinkTexts("https://example.com/\ntwo-lines").single()
    assertThat(fixture.uriOf(highlighter)).isEqualTo(uri)
  }

  @Test
  fun `a URL split over three lines with an indent is merged into one hyperlink`() = doTest { fixture ->
    val uri = "https://example.com/three/lines"
    fixture.connector.feed(osc8(uri, "https://") + "\r\n  " + osc8(uri, "example.com/") + "  \r\n  " + osc8(uri, "three/lines"))

    val highlighter = fixture.awaitHyperlinkTexts("https://\n  example.com/  \n  three/lines").single()
    assertThat(fixture.uriOf(highlighter)).isEqualTo(uri)
  }

  @Test
  fun `parts of a URL separated by an empty line are not merged`() = doTest { fixture ->
    val uri = "https://example.com/empty-line"
    fixture.connector.feed(osc8(uri, "https://example.com/") + "\r\n\r\n" + osc8(uri, "empty-line"))

    fixture.awaitHyperlinkTexts("https://example.com/", "empty-line")
  }

  @Test
  fun `parts of a URL on the same line separated by a space are not merged`() = doTest { fixture ->
    val uri = "https://example.com/same-line"
    fixture.connector.feed(osc8(uri, "https://example.com/") + " " + osc8(uri, "same-line"))

    fixture.awaitHyperlinkTexts("https://example.com/", "same-line")
  }

  @Test
  fun `parts with the same URI are not merged if their text is not the URI`() = doTest { fixture ->
    val uri = "https://example.com/docs"
    fixture.connector.feed(osc8(uri, "see the") + "\r\n" + osc8(uri, "docs"))

    fixture.awaitHyperlinkTexts("see the", "docs")
  }

  @Test
  fun `parts are not merged if their joined text has the URI length but differs from the URI`() = doTest { fixture ->
    val uri = "https://example.com/ab"
    fixture.connector.feed(osc8(uri, "https://example.com/") + "\r\n" + osc8(uri, "xy"))

    fixture.awaitHyperlinkTexts("https://example.com/", "xy")
  }

  @Test
  fun `a URL printed twice on neighboring lines stays two hyperlinks`() = doTest { fixture ->
    val uri = "https://example.com/twice"
    fixture.connector.feed(osc8(uri, uri) + "\r\n" + osc8(uri, uri))

    fixture.awaitHyperlinkTexts(uri, uri)
  }

  @Test
  fun `parts of URLs with different targets are not merged`() = doTest { fixture ->
    fixture.connector.feed(osc8("https://example.com/", "https://example.com/") + "\r\n" + osc8("https://example.com/other", "other"))

    fixture.awaitHyperlinkTexts("https://example.com/", "other")
  }

  @Test
  fun `parts of a URL separated by other text are not merged`() = doTest { fixture ->
    val uri = "https://example.com/other-text"
    fixture.connector.feed(osc8(uri, "https://example.com/") + " x\r\n" + osc8(uri, "other-text"))

    fixture.awaitHyperlinkTexts("https://example.com/", "other-text")
  }

  @Test
  fun `hovering the second line of a merged URL shows its target URI as a tooltip`() = doTest { fixture ->
    fixture.resize(columns = 80, rows = 24)
    val uri = "https://example.com/hover"
    fixture.connector.feed(osc8(uri, "https://example.com/") + "\r\n" + osc8(uri, "hover"))

    val highlighter = fixture.awaitHyperlinkTexts("https://example.com/\nhover").single()
    fixture.hover(highlighter.endOffset - 2)

    assertThat(fixture.view.outputEditor.contentComponent.toolTipText).contains(uri)
  }

  @Test
  fun `OSC8 hyperlink in the alternate screen is rendered in the alternate buffer editor`() = doTest { fixture ->
    fixture.enterAlternateScreen()
    fixture.connector.feed("before ${osc8("https://example.com", "link text")} after")

    val highlighter = fixture.awaitHyperlink()
    assertThat(fixture.textOf(highlighter)).isEqualTo("link text")
    assertThat(fixture.uriOf(highlighter)).isEqualTo("https://example.com")
  }

  @Test
  fun `OSC8 hyperlink in the same chunk as the switch to the alternate screen is rendered`() = doTest { fixture ->
    // The alternate model gets the link before the view switches the editors.
    fixture.enterAlternateScreen(sameChunkText = "before ${osc8("https://example.com", "link text")} after")

    val highlighter = fixture.awaitHyperlink()
    assertThat(fixture.textOf(highlighter)).isEqualTo("link text")
    assertThat(fixture.uriOf(highlighter)).isEqualTo("https://example.com")
  }

  @Test
  fun `hovering an OSC8 hyperlink in the alternate screen shows its target URI as a tooltip`() = doTest { fixture ->
    fixture.enterAlternateScreen()
    fixture.resize(columns = 80, rows = 24)
    fixture.connector.feed("x ${osc8("https://jetbrains.com", "JB")} y")

    val highlighter = fixture.awaitHyperlink()
    fixture.hover(highlighter)

    assertThat(fixture.activeEditor.contentComponent.toolTipText).contains("https://jetbrains.com")
  }

  private fun osc8(uri: String, text: String): String = "$OSC8_PREFIX$uri$ST$text$OSC8_PREFIX$ST"

  /** Enters the alternate screen with [sameChunkText] in the same chunk, and waits until the view shows the alternate buffer. */
  private suspend fun TerminalViewFixture.enterAlternateScreen(sameChunkText: String = "") {
    connector.feed("$ESC[?1049h$sameChunkText")
    view.outputModels.active.first { it === view.outputModels.alternative }
  }

  companion object {
    private val ESC: String = Char(0x1B).toString()

    /** OSC 8 introducer with empty params: `ESC ] 8 ; ;`. */
    private val OSC8_PREFIX: String = "$ESC]8;;"

    /** String Terminator: `ESC \`. */
    private val ST: String = "$ESC\\"
  }
}

// ---------------------------------------------------------------------------
// Hyperlink-specific TerminalViewFixture extensions.
// ---------------------------------------------------------------------------

private fun TerminalViewFixture.textOf(highlighter: RangeHighlighter): String {
  return activeEditor.document.getText(highlighter.textRange)
}

/**
 * The target URI of the OSC8 link rendered as [highlighter], read from the active output model - the markup model's
 * own decoration doesn't expose it (it's only used internally to build the click action).
 *
 * A highlighter of merged links starts where its first link starts, so the start offset is enough to find it.
 */
private fun TerminalViewFixture.uriOf(highlighter: RangeHighlighter): String {
  val model = view.outputModels.active.value
  return model.getOsc8Hyperlinks().single {
    (it.startOffset - model.startOffset).toInt() == highlighter.startOffset
  }.uri
}

/** Moves the mouse over the middle of [highlighter]'s range, as a real mouse move would. */
private fun TerminalViewFixture.hover(highlighter: RangeHighlighter) {
  hover((highlighter.startOffset + highlighter.endOffset) / 2)
}

/** Moves the mouse over [offset] of the active editor, as a real mouse move would. */
private fun TerminalViewFixture.hover(offset: Int) {
  val editor = activeEditor
  val point = editor.offsetToXY(offset)
  val event = MouseEvent(
    editor.contentComponent, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, point.x, point.y, 1, false, MouseEvent.BUTTON1
  )
  editor.contentComponent.mouseMotionListeners.forEach { it.mouseMoved(event) }
}

private suspend fun TerminalViewFixture.awaitHyperlink(): RangeHighlighter = awaitHyperlinks(1).single()

/**
 * Polls the active editor's markup model until the hyperlink highlighters cover exactly [texts], then returns
 * them sorted by position.
 */
private suspend fun TerminalViewFixture.awaitHyperlinkTexts(vararg texts: String): List<RangeHighlighter> {
  while (true) {
    val highlighters = awaitHyperlinks(texts.size)
    if (highlighters.map { textOf(it) } == texts.toList()) return highlighters
    delay(50.milliseconds)
  }
}

/**
 * Polls the active editor's markup model until exactly [count] hyperlink highlighters are present, then returns
 * them sorted by position.
 */
private suspend fun TerminalViewFixture.awaitHyperlinks(count: Int): List<RangeHighlighter> {
  val editor = activeEditor
  while (true) {
    val highlighters = editor.markupModel.allHighlighters.filter { it.isValid && it.layer == HighlighterLayer.HYPERLINK }
    if (highlighters.size == count) return highlighters.sortedBy { it.startOffset }
    delay(50.milliseconds)
  }
}
