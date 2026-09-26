// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session.jediterm

import com.intellij.terminal.frontend.session.jediterm.TerminalContentChangesTracker
import com.intellij.terminal.frontend.session.jediterm.TerminalContentUpdate
import com.intellij.terminal.frontend.session.jediterm.TerminalDiscardedHistoryTracker
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jediterm.terminal.HyperlinkStyle
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.CharBuffer
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer
import org.jetbrains.plugins.terminal.session.impl.JediTermOsc8LinkInfo
import org.jetbrains.plugins.terminal.session.impl.dto.Osc8HyperlinkDto
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Verifies that the output scraper extracts OSC8 hyperlink spans out of [HyperlinkStyle] cells
 * (see [org.jetbrains.plugins.terminal.block.session.scraper.StylesCollectingTerminalLinesCollector]).
 *
 * The cells are written directly with a [HyperlinkStyle] carrying a [JediTermOsc8LinkInfo], which is exactly
 * what JediTerm produces for an `OSC 8` sequence once `JediTermOsc8HyperlinkFilter` is installed.
 */
@RunWith(JUnit4::class)
internal class TerminalOsc8HyperlinkScraperTest : BasePlatformTestCase() {
  @Test
  fun `single link surrounded by plain text`() {
    val term = createTerminal(width = 40, height = 3)

    val link = HyperlinkStyle(TextStyle.EMPTY, JediTermOsc8LinkInfo("https://example.com"))
    term.writePlain("before ", y = 1, x = 0)
    term.write("link", link, y = 1, x = 7)
    term.writePlain(" after", y = 1, x = 11)

    val update = term.tracker.getContentUpdate() ?: error("Update is null")
    assertOsc8(update, expected = listOf(Osc8Expectation("link", "https://example.com")))
  }

  @Test
  fun `two distinct adjacent links are not merged`() {
    val term = createTerminal(width = 40, height = 3)

    val first = HyperlinkStyle(TextStyle.EMPTY, JediTermOsc8LinkInfo("https://first.example"))
    val second = HyperlinkStyle(TextStyle.EMPTY, JediTermOsc8LinkInfo("https://second.example"))
    term.write("aaa", first, y = 1, x = 0)
    term.write("bbb", second, y = 1, x = 3)

    val update = term.tracker.getContentUpdate() ?: error("Update is null")
    assertOsc8(update, expected = listOf(
      Osc8Expectation("aaa", "https://first.example"),
      Osc8Expectation("bbb", "https://second.example"),
    ))
  }

  @Test
  fun `two adjacent links with the same uri are merged even as distinct instances`() {
    val term = createTerminal(width = 40, height = 3)

    // Two distinct HyperlinkStyle/JediTermOsc8LinkInfo instances (as two separate OSC8 sequences always
    // produce, see JediTermOsc8HyperlinkFilter) but with the same URI: JediTermOsc8LinkInfo compares by
    // URI, so the collector must still merge these adjacent runs into a single link.
    val first = HyperlinkStyle(TextStyle.EMPTY, JediTermOsc8LinkInfo("https://example.com"))
    val second = HyperlinkStyle(TextStyle.EMPTY, JediTermOsc8LinkInfo("https://example.com"))
    term.write("aaa", first, y = 1, x = 0)
    term.write("bbb", second, y = 1, x = 3)

    val update = term.tracker.getContentUpdate() ?: error("Update is null")
    assertOsc8(update, expected = listOf(Osc8Expectation("aaabbb", "https://example.com")))
  }

  @Test
  fun `plain text between two links with the same uri keeps them separate`() {
    val term = createTerminal(width = 40, height = 3)

    // The same URI but two distinct OSC8 sequences => two distinct HyperlinkStyle instances.
    val first = HyperlinkStyle(TextStyle.EMPTY, JediTermOsc8LinkInfo("https://example.com"))
    val second = HyperlinkStyle(TextStyle.EMPTY, JediTermOsc8LinkInfo("https://example.com"))
    term.write("one", first, y = 1, x = 0)
    term.writePlain(" and ", y = 1, x = 3)
    term.write("two", second, y = 1, x = 8)

    val update = term.tracker.getContentUpdate() ?: error("Update is null")
    assertOsc8(update, expected = listOf(
      Osc8Expectation("one", "https://example.com"),
      Osc8Expectation("two", "https://example.com"),
    ))
  }

  @Test
  fun `link is merged across a wrapped line`() {
    val term = createTerminal(width = 3, height = 3)

    // One OSC8 link (one HyperlinkStyle instance) that spans a wrapped line boundary.
    val link = HyperlinkStyle(TextStyle.EMPTY, JediTermOsc8LinkInfo("https://example.com"))
    term.write("abc", link, y = 1, x = 0)
    term.buffer.getLine(0).isWrapped = true
    term.write("def", link, y = 2, x = 0)

    val update = term.tracker.getContentUpdate() ?: error("Update is null")
    assertOsc8(update, expected = listOf(Osc8Expectation("abcdef", "https://example.com")))
  }

  @Test
  fun `link is not merged across a hard line break`() {
    val term = createTerminal(width = 3, height = 3)

    // The same OSC8 link instance on two lines that are NOT wrapped: the line break must split it,
    // because the emitted "\n" makes the two spans non-adjacent.
    val link = HyperlinkStyle(TextStyle.EMPTY, JediTermOsc8LinkInfo("https://example.com"))
    term.write("abc", link, y = 1, x = 0)
    term.write("def", link, y = 2, x = 0)

    val update = term.tracker.getContentUpdate() ?: error("Update is null")
    assertOsc8(update, expected = listOf(
      Osc8Expectation("abc", "https://example.com"),
      Osc8Expectation("def", "https://example.com"),
    ))
  }

  private fun assertOsc8(update: TerminalContentUpdate, expected: List<Osc8Expectation>) {
    val actual = update.osc8Hyperlinks.map { link: Osc8HyperlinkDto ->
      Osc8Expectation(update.text.substring(link.startOffset.toInt(), link.endOffset.toInt()), link.uri)
    }
    assertEquals(expected, actual)
  }

  private data class Osc8Expectation(val text: String, val uri: String)

  private class TestTerminal(val buffer: TerminalTextBuffer, private val styleState: StyleState) {
    val tracker: TerminalContentChangesTracker = TerminalContentChangesTracker(buffer, TerminalDiscardedHistoryTracker(buffer))

    fun writePlain(text: String, y: Int, x: Int) = write(text, TextStyle.EMPTY, y, x)

    fun write(text: String, style: TextStyle, y: Int, x: Int) {
      styleState.current = style
      buffer.writeString(x, y, CharBuffer(text))
      styleState.reset()
    }
  }

  @Suppress("SameParameterValue")
  private fun createTerminal(width: Int, height: Int): TestTerminal {
    val styleState = StyleState()
    val buffer = TerminalTextBuffer(width, height, styleState, 10)
    return TestTerminal(buffer, styleState)
  }
}
