// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked

import com.intellij.openapi.application.EDT
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.terminal.session.impl.Osc8Hyperlink
import org.jetbrains.plugins.terminal.session.impl.dto.toDto
import org.jetbrains.plugins.terminal.session.impl.dto.toState
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Verifies that [MutableTerminalOutputModel] stores OSC8 hyperlinks with absolute offsets and keeps them
 * consistent across insertions (shift), overwrites (trim/split), trimming (removeBefore), clearing, and
 * state dump/restore.
 */
@RunWith(JUnit4::class)
internal class TerminalOutputModelOsc8Test : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `basic link is stored with absolute offsets`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel()
    model.updateContent(0, "hello world", emptyList(), listOf(Osc8Hyperlink(6, 11, URI)))

    assertOsc8(model, listOf(Osc8Triple(6, 11, URI)))
  }

  @Test
  fun `link is shifted by an insertion before it`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel()
    model.updateContent(0, "hello world", emptyList(), listOf(Osc8Hyperlink(6, 11, URI)))

    // Insert two characters at the very start of the document.
    model.replaceContent(model.startOffset, 0, "XX", emptyList())

    assertOsc8(model, listOf(Osc8Triple(8, 13, URI)))
  }

  @Test
  fun `link before the trim boundary is dropped, one after is kept`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel(maxLength = 10)
    // 15 chars, max 10 => the first 5 chars are trimmed.
    model.updateContent(0, "0123456789ABCDE", emptyList(), listOf(
      Osc8Hyperlink(0, 3, "https://trimmed"),
      Osc8Hyperlink(12, 15, URI),
    ))

    assertOsc8(model, listOf(Osc8Triple(12, 15, URI)))
  }

  @Test
  fun `link straddling the trim boundary is dropped entirely`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel(maxLength = 10)
    // 15 chars, max 10 => the first 5 chars are trimmed; the link starts before the trim point and ends after it.
    // The still-visible suffix of the link is intentionally dropped together with the trimmed part,
    // same as highlightings behave (see `AbsoluteOffsetRanges.removeBefore`).
    model.updateContent(0, "0123456789ABCDE", emptyList(), listOf(Osc8Hyperlink(3, 8, URI)))

    assertOsc8(model, emptyList())
  }

  @Test
  fun `link keeps only the part that was not overwritten`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel()
    model.updateContent(0, "hello world", emptyList(), listOf(Osc8Hyperlink(6, 11, URI)))

    // Rewrite "rld" - the tail of the linked "world".
    model.replaceContent(model.startOffset + 8L, 3, "RLD", emptyList())

    assertOsc8(model, listOf(Osc8Triple(6, 8, URI)))
  }

  @Test
  fun `link keeps only its tail when the beginning is overwritten`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel()
    model.updateContent(0, "hello world", emptyList(), listOf(Osc8Hyperlink(6, 11, URI)))

    // Rewrite "wor" - the head of the linked "world", leaving "ld" linked.
    model.replaceContent(model.startOffset + 6, 3, "WOR", emptyList())

    assertOsc8(model, listOf(Osc8Triple(9, 11, URI)))
  }

  @Test
  fun `link is split when its middle is overwritten`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel()
    model.updateContent(0, "hello world", emptyList(), listOf(Osc8Hyperlink(6, 11, URI)))

    // Rewrite "r" in the middle of the linked "world": the link falls apart into "wo" and "ld",
    // and the rewritten character is left out of both. This is what a type-ahead insertion inside
    // a link does, and it matches how highlightings behave.
    model.replaceContent(model.startOffset + 8L, 1, "R", emptyList())

    assertOsc8(model, listOf(Osc8Triple(6, 8, URI), Osc8Triple(9, 11, URI)))
  }

  @Test
  fun `link is dropped when it is fully overwritten`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel()
    model.updateContent(0, "hello world", emptyList(), listOf(Osc8Hyperlink(6, 11, URI)))

    // Rewrite exactly the linked "world".
    model.replaceContent(model.startOffset + 6L, 5, "WORLD", emptyList())

    assertOsc8(model, emptyList())
  }

  @Test
  fun `clearing the output drops all links`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel(maxLength = 5)
    // 11 chars over 3 lines, max 5 => the first line and a part of the second one are trimmed.
    model.updateContent(0, "aaa\nbbb\nccc", emptyList(), listOf(Osc8Hyperlink(8, 11, URI)))
    assertEquals(6L, model.startOffset.toAbsolute())
    assertOsc8(model, listOf(Osc8Triple(8, 11, URI)))

    // An update starting below the already trimmed part means the terminal was cleared: the model resets.
    model.updateContent(0, "fresh", emptyList())

    assertOsc8(model, emptyList())
  }

  @Test
  fun `links survive state dump and restore`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel()
    model.updateContent(0, "hello world", emptyList(), listOf(Osc8Hyperlink(0, 5, "https://a"), Osc8Hyperlink(6, 11, URI)))

    val state = model.dumpState()
    val restored = TerminalTestUtil.createOutputModel()
    restored.restoreFromState(state)

    assertOsc8(restored, listOf(Osc8Triple(0, 5, "https://a"), Osc8Triple(6, 11, URI)))
  }

  @Test
  fun `links survive the state DTO round trip`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel()
    model.updateContent(0, "hello world", emptyList(), listOf(Osc8Hyperlink(0, 5, "https://a"), Osc8Hyperlink(6, 11, URI)))

    val restored = TerminalTestUtil.createOutputModel()
    restored.restoreFromState(model.dumpState().toDto().toState())

    assertOsc8(restored, listOf(Osc8Triple(0, 5, "https://a"), Osc8Triple(6, 11, URI)))
  }

  private fun assertOsc8(model: TerminalOutputModel, expected: List<Osc8Triple>) {
    val actual = model.getOsc8Hyperlinks().map { Osc8Triple(it.startOffset.toAbsolute(), it.endOffset.toAbsolute(), it.uri) }
    assertEquals(expected, actual)
  }

  private data class Osc8Triple(val startOffset: Long, val endOffset: Long, val uri: String)

  private companion object {
    const val URI: String = "https://example.com"
  }
}
