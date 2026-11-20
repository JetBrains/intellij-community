// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session.ketraterm

import com.intellij.terminal.frontend.session.ketraterm.KetraTermOutputProjector
import com.intellij.terminal.frontend.session.ketraterm.collectKetraTermProjections
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import io.github.ketraterm.core.TerminalBuffers
import io.github.ketraterm.render.api.TerminalColorPalette
import io.github.ketraterm.render.api.TerminalRenderFrame
import io.github.ketraterm.session.TerminalSession
import io.github.ketraterm.transport.TerminalConnector
import io.github.ketraterm.transport.TerminalConnectorListener
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.dto.StyleRangeDto
import org.jetbrains.plugins.terminal.session.impl.dto.TerminalColorDto
import org.jetbrains.plugins.terminal.session.impl.dto.TextStyleDto
import org.jetbrains.plugins.terminal.session.impl.dto.TextStyleOptionDto
import org.junit.jupiter.api.Test

internal class KetraTermOutputProjectorTest {
  @Test
  fun `an unchanged frame reuses row metadata`() = withSession(maxHistory = 4000) { session, connector, projector ->
    project(session, projector)
    connector.feed((0 until 1000).joinToString("") { "l$it\r\n" } + "tail")
    project(session, projector)
    session.readRenderFrameForAbsoluteRange(0, Long.MAX_VALUE) { frame ->
      val measured = MetadataCountingFrame(frame)
      assertThat(projector.project(measured)).isNull()
      assertThat(measured.metadataReads).isZero()
    }
    assertThat(projector.cursor.logicalLineIndex).isEqualTo(1000)
    assertThat(projector.cursor.columnIndex).isEqualTo(4)
  }

  @Test
  fun `a visual change inspects row generations when content generation stays unchanged`() = withSession {
    session, connector, projector ->
    project(session, projector)
    connector.feed("plain")
    project(session, projector)
    var previousContentGeneration = 0L
    session.readRenderFrameForAbsoluteRange(0, Long.MAX_VALUE) { previousContentGeneration = it.contentGeneration }
    connector.feed("\r\u001b[31mplain")
    session.readRenderFrameForAbsoluteRange(0, Long.MAX_VALUE) { frame ->
      val presentation = object : TerminalRenderFrame by frame {
        override val contentGeneration = previousContentGeneration
      }
      val measured = MetadataCountingFrame(presentation)
      val actual = requireNotNull(projector.project(measured))
      assertThat(measured.metadataReads).isGreaterThan(0)
      assertThat(actual.text).isEqualTo("plain")
      assertThat(actual.styles.single().style.foreground).isEqualTo(TerminalColorDto(1, null))
    }
  }

  @Test
  fun `pending history reuses metadata and completes logical positions`() = withSession(maxHistory = 4000) {
    session, connector, projector ->
    project(session, projector)
    val payload = (0 until 1000).joinToString("") { "line $it abcdefghijklmnop\r\n" } + "MARKER"
    connector.feed(payload)
    val visible = requireNotNull(project(session, projector, prioritizeVisible = true))
    assertThat(projector.hasPendingHistory).isTrue()
    session.readRenderFrameForAbsoluteRange(0, Long.MAX_VALUE) { frame ->
      val measured = MetadataCountingFrame(frame)
      val complete = requireNotNull(projector.project(measured))
      assertThat(measured.metadataReads).isZero()
      assertThat(complete.text).isEqualTo(payload.replace("\r\n", "\n"))
      assertThat(complete.cursorLogicalLineIndex).isEqualTo(visible.cursorLogicalLineIndex)
      assertThat(complete.cursorColumnIndex).isEqualTo(visible.cursorColumnIndex)
      assertThat(complete.screenTopLogicalLineIndex).isEqualTo(visible.screenTopLogicalLineIndex)
      assertThat(complete.screenTopColumnIndex).isEqualTo(visible.screenTopColumnIndex)
    }
    assertThat(projector.hasPendingHistory).isFalse()
  }

  @Test
  fun `cursor motion preserves positions when the screen starts inside a wrapped line`() = withSession(maxHistory = 4000) {
    session, connector, projector ->
    project(session, projector)
    connector.feed("prefix\r\n" + "abcdef".repeat(1000) + "\r\ntail")
    val before = requireNotNull(project(session, projector))
    assertThat(before.screenTopColumnIndex).isGreaterThan(0)
    for (motion in listOf("\u001b[1;1H", "\u001b[3;3H", "\u001b[2;7H")) {
      connector.feed(motion)
      val actual = project(session, projector)
      assertProjectedPositions(session, projector, actual)
    }
  }

  @Test
  fun `tail changes and history admission preserve cached screen positions`() = withSession(maxHistory = 4000) {
    session, connector, projector ->
    project(session, projector)
    connector.feed("prefix\r\n" + "abcdef".repeat(1000) + "\r\ntail")
    val before = requireNotNull(project(session, projector))
    assertThat(before.screenTopColumnIndex).isGreaterThan(0)
    for (mutation in listOf("\rtail2", "\r\nnext", "\r\n你好 e\u0301", "\r\nlast")) {
      connector.feed(mutation)
      val actual = requireNotNull(project(session, projector))
      assertProjectedPositions(session, projector, actual)
    }
  }

  @Test
  fun `a wrapped line edit before the cached anchor recomputes its positions`() = withSession(maxHistory = 4000) {
    session, connector, projector ->
    project(session, projector)
    connector.feed("prefix\r\n" + "abcdef".repeat(1000) + "\r\ntail")
    project(session, projector)
    connector.feed("\u001b[1;1HZ")
    val actual = requireNotNull(project(session, projector))
    assertProjectedPositions(session, projector, actual)
    val complete = requireNotNull(project(session, KetraTermOutputProjector()))
    assertThat(actual.startLineLogicalIndex).isEqualTo(1)
    assertThat(actual.text).isEqualTo(complete.text.removePrefix("prefix\n"))
  }

  @Test
  fun `indexed colors preserve ANSI indices and resolve extended colors`() {
    val colors = TerminalColorPalette.defaultIndexedColors()
    colors[15] = 0xFF123456.toInt()
    colors[16] = 0xFF2468AC.toInt()
    colors[17] = 0xFF3579BD.toInt()
    colors[255] = 0xFF456789.toInt()
    val palette = TerminalColorPalette(indexedColors = colors)
    for (index in listOf(15, 16, 17, 255)) {
      withSession { session, connector, projector ->
        session.setThemePalette(palette)
        project(session, projector)
        connector.feed("\u001b[38;5;$index;48;5;${index}mX")
        val event = requireNotNull(project(session, projector))
        val color = if (index == 15) TerminalColorDto(15, null) else TerminalColorDto(null, colors[index] and 0xFFFFFF)
        assertThat(event.text).isEqualTo("X")
        assertThat(event.styles).describedAs("palette index $index")
          .containsExactly(StyleRangeDto(0, 1, TextStyleDto(color, color, emptyList()), false))
      }
    }
  }

  @Test
  fun `RGB and default colors retain inverse semantics`() = withSession { session, connector, projector ->
    project(session, projector)
    connector.feed("\u001b[38;2;12;34;56;48;2;78;90;123mR\u001b[0;7mI\u001b[0;38;5;17;48;5;255;7mX\u001b[0mD")
    val event = requireNotNull(project(session, projector))
    val inverse = listOf(TextStyleOptionDto.INVERSE)
    assertThat(event.text).isEqualTo("RIXD")
    assertThat(event.styles).containsExactly(
      StyleRangeDto(0, 1, TextStyleDto(TerminalColorDto(null, 0x0C2238), TerminalColorDto(null, 0x4E5A7B), emptyList()), false),
      StyleRangeDto(1, 2, TextStyleDto(null, null, inverse), false),
      StyleRangeDto(2, 3, TextStyleDto(TerminalColorDto(null, 0x00005F), TerminalColorDto(null, 0xEEEEEE), inverse), false),
    )
  }

  @Test
  fun `a palette replacement updates existing styles without output`() = withSession { session, connector, projector ->
    project(session, projector)
    connector.feed("\u001b[38;5;16;48;5;17mX")
    val before = requireNotNull(project(session, projector))
    assertThat(project(session, projector)).isNull()
    val colors = session.palette.toIndexedColorsArray()
    colors[16] = 0xFF13579B.toInt()
    colors[17] = 0xFF2468AC.toInt()
    session.setThemePalette(session.palette.copy(indexedColors = colors))
    val after = requireNotNull(project(session, projector))
    assertThat(after.text).isEqualTo(before.text)
    assertThat(after.startLineLogicalIndex).isEqualTo(before.startLineLogicalIndex)
    assertThat(after.styles).containsExactly(
      StyleRangeDto(0, 1, TextStyleDto(TerminalColorDto(null, 0x13579B), TerminalColorDto(null, 0x2468AC), emptyList()), false),
    )
    assertThat(project(session, projector)).isNull()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `history waits for quiet input and visible updates coalesce`() = runTest {
    val generation = MutableStateFlow(0L)
    val calls = ArrayList<Pair<Boolean, Long>>()
    backgroundScope.launch {
      generation.collectKetraTermProjections { visible ->
        calls.add(visible to generation.value)
        visible
      }
    }
    runCurrent()
    generation.value = 1
    generation.value = 2
    advanceTimeBy(4)
    runCurrent()
    assertThat(calls).containsExactly(true to 2L)
    generation.value = 3
    runCurrent()
    advanceTimeBy(4)
    runCurrent()
    assertThat(calls).containsExactly(true to 2L, true to 3L)
    advanceTimeBy(8)
    runCurrent()
    assertThat(calls).containsExactly(true to 2L, true to 3L, false to 3L)
    advanceTimeBy(100)
    runCurrent()
    assertThat(calls).hasSize(3)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `continuous output cannot starve history`() = runTest {
    val generation = MutableStateFlow(0L)
    val calls = ArrayList<Boolean>()
    backgroundScope.launch {
      generation.collectKetraTermProjections { visible ->
        calls.add(visible)
        if (visible) generation.value++
        visible
      }
    }
    runCurrent()
    repeat(16) {
      advanceTimeBy(4)
      runCurrent()
    }
    assertThat(calls).hasSize(17)
    assertThat(calls.take(16)).containsOnly(true)
    assertThat(calls.last()).isFalse()
  }
  @Test
  fun `visible and history updates preserve logical positions`() = withSession(maxHistory = 4000) { session, connector, projector ->
    project(session, projector)
    connector.feed("previous\r\n")
    project(session, projector)
    val payload = (0 until 1000).joinToString("") { "line $it abcdefghijklmnop\r\n" } + "MARKER"
    connector.feed(payload)
    val visible = requireNotNull(project(session, projector, prioritizeVisible = true))
    assertThat(visible.text).endsWith("MARKER").hasSizeLessThan(100)
    assertThat(visible.startLineLogicalIndex).isGreaterThan(900)
    assertThat(visible.cursorLogicalLineIndex).isEqualTo(1001)
    assertThat(projector.hasPendingHistory).isTrue()
    val complete = requireNotNull(project(session, projector))
    assertThat(complete.text).isEqualTo(payload.replace("\r\n", "\n"))
    assertThat(complete.startLineLogicalIndex).isEqualTo(1)
    assertThat(complete.cursorLogicalLineIndex).isEqualTo(visible.cursorLogicalLineIndex)
    assertThat(complete.cursorColumnIndex).isEqualTo(visible.cursorColumnIndex)
    assertThat(complete.screenTopLogicalLineIndex).isEqualTo(visible.screenTopLogicalLineIndex)
    assertThat(complete.screenTopColumnIndex).isEqualTo(visible.screenTopColumnIndex)
    assertThat(projector.hasPendingHistory).isFalse()
    assertThat(project(session, projector)).isNull()
  }

  @Test
  fun `history completion reads current content after intervening mutations`() {
    for (mutation in listOf("\rLATEST", "\u001b[3J\u001b[2J\u001b[Hcleared", "\u001b[?1049halternate\u001b[?1049lafter")) {
      withSession(maxHistory = 4000) { session, connector, projector ->
        project(session, projector)
        connector.feed((0 until 1000).joinToString("") { "line $it\r\n" } + "MARKER")
        project(session, projector, prioritizeVisible = true)
        assertThat(projector.hasPendingHistory).isTrue()
        connector.feed(mutation)
        val actual = requireNotNull(project(session, projector))
        val expected = requireNotNull(project(session, KetraTermOutputProjector()))
        assertThat(actual).describedAs(mutation).isEqualTo(expected)
        assertThat(projector.hasPendingHistory).isFalse()
      }
    }
  }

  @Test
  fun `resize between phases preserves logical lines`() = withSession(maxHistory = 4000) { session, connector, projector ->
    project(session, projector)
    val payload = (0 until 700).joinToString("") { "line $it abcdefghijklmnop\r\n" } + "MARKER"
    connector.feed(payload)
    project(session, projector, prioritizeVisible = true)
    assertThat(projector.hasPendingHistory).isTrue()
    session.resize(20, 3)
    val actual = requireNotNull(project(session, projector))
    assertThat(actual).isEqualTo(project(session, KetraTermOutputProjector()))
    assertThat(actual.text).isEqualTo(payload.replace("\r\n", "\n"))
    assertThat(projector.hasPendingHistory).isFalse()
  }

  @Test
  fun `alternate screen does not consume pending primary history`() = withSession(maxHistory = 4000) { session, connector, projector ->
    project(session, projector)
    val payload = (0 until 700).joinToString("") { "line $it\r\n" } + "MARKER"
    connector.feed(payload)
    project(session, projector, prioritizeVisible = true)
    assertThat(projector.hasPendingHistory).isTrue()
    connector.feed("\u001b[?1049halt")
    assertThat(requireNotNull(project(session, projector)).text).isEqualTo("alt")
    assertThat(projector.hasPendingHistory).isFalse()
    connector.feed("\u001b[?1049l")
    assertThat(requireNotNull(project(session, projector)).text).isEqualTo(payload.replace("\r\n", "\n"))
    assertThat(projector.hasPendingHistory).isFalse()
  }

  @Test
  fun `history eviction between phases retains every surviving line`() = withSession(maxHistory = 800) { session, connector, projector ->
    project(session, projector)
    connector.feed((0 until 700).joinToString("") { "line $it\r\n" })
    project(session, projector, prioritizeVisible = true)
    assertThat(projector.hasPendingHistory).isTrue()
    connector.feed((700 until 1000).joinToString("") { "line $it\r\n" } + "MARKER")
    val complete = requireNotNull(project(session, projector))
    val expected = requireNotNull(project(session, KetraTermOutputProjector()))
    assertThat(complete.text).isEqualTo(expected.text)
    assertThat(complete.cursorLogicalLineIndex - complete.startLineLogicalIndex)
      .isEqualTo(expected.cursorLogicalLineIndex - expected.startLineLogicalIndex)
    assertThat(projector.hasPendingHistory).isFalse()
  }

  @Test
  fun `visible prioritization never splits a logical line`() = withSession(maxHistory = 4000) { session, connector, projector ->
    project(session, projector)
    val payload = "abcdef".repeat(1000)
    connector.feed(payload)
    val event = requireNotNull(project(session, projector, prioritizeVisible = true))
    assertThat(event.text).isEqualTo(payload)
    assertThat(projector.hasPendingHistory).isFalse()
  }

  @Test
  fun `resize preserves complete logical lines and their indices`() = withSession { session, connector, projector ->
    project(session, projector)
    connector.feed("abcdefghijklmnopqr\r\ntail")
    val before = project(session, projector)!!
    assertThat(before.text).isEqualTo("abcdefghijklmnopqr\ntail")
    session.resize(6, 3)
    val after = project(session, projector)!!
    assertThat(after.text).isEqualTo(before.text)
    assertThat(after.startLineLogicalIndex).isEqualTo(before.startLineLogicalIndex)
    assertThat(after.cursorLogicalLineIndex).isEqualTo(1)
    assertThat(after.cursorColumnIndex).isEqualTo(4)
  }

  @Test
  fun `resize retains the logical base after old history is discarded`() = withSession { session, connector, projector ->
    project(session, projector)
    for (line in 0 until 8) {
      connector.feed("l$line\r\n")
      project(session, projector)
    }
    connector.feed("tail")
    project(session, projector)
    session.resize(12, 3)
    val event = project(session, projector)!!
    assertThat(event.startLineLogicalIndex).isEqualTo(3)
    assertThat(event.text).isEqualTo("l3\nl4\nl5\nl6\nl7\ntail")
    assertThat(event.cursorLogicalLineIndex).isEqualTo(8)
    assertThat(event.cursorColumnIndex).isEqualTo(4)
  }

  @Test
  fun `a missed retained range replaces the mirror from a known origin`() = withSession { session, connector, projector ->
    project(session, projector)
    connector.feed((0 until 20).joinToString("") { "l$it\r\n" } + "END")
    val event = project(session, projector)!!
    assertThat(event.startLineLogicalIndex).isZero()
    assertThat(event.text).isEqualTo("l15\nl16\nl17\nl18\nl19\nEND")
    assertThat(event.cursorLogicalLineIndex).isEqualTo(5)
    assertThat(event.cursorColumnIndex).isEqualTo(3)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `a paused reader with queued input does not trigger early history`() = runTest {
    val generation = MutableStateFlow(0L)
    var pending = true
    val calls = ArrayList<Boolean>()
    backgroundScope.launch {
      generation.collectKetraTermProjections({ pending }) { visible ->
        calls.add(visible)
        visible
      }
    }
    runCurrent()
    advanceTimeBy(24)
    runCurrent()
    assertThat(calls).containsExactly(true, true)
    pending = false
    advanceTimeBy(12)
    runCurrent()
    assertThat(calls).containsExactly(true, true, true, false)
  }
  @Test
  fun `unpublished output leaves projection state untouched`() = withSession { session, connector, projector ->
    project(session, projector)
    connector.feed("new output")
    session.readRenderFrameForAbsoluteRange(0, Long.MAX_VALUE) { frame ->
      val previousCursor = projector.cursor
      assertThat(projector.project(frame, publishedFrameGeneration = frame.frameGeneration xor 1L)).isNull()
      assertThat(projector.cursor).isSameAs(previousCursor)
      val content = requireNotNull(projector.project(frame, publishedFrameGeneration = frame.frameGeneration))
      assertThat(content.text).isEqualTo("new output")
      assertThat(projector.project(frame, publishedFrameGeneration = frame.frameGeneration)).isNull()
    }
  }

  @Test
  fun `unpublished output preserves pending history until native publication`() = withSession(maxHistory = 4000) {
    session, connector, projector ->
    project(session, projector)
    connector.feed((0 until 1000).joinToString("") { "line $it\r\n" } + "old")
    project(session, projector, prioritizeVisible = true)
    assertThat(projector.hasPendingHistory).isTrue()
    connector.feed("\rLATEST")
    session.readRenderFrameForAbsoluteRange(0, Long.MAX_VALUE) { frame ->
      assertThat(projector.project(frame, publishedFrameGeneration = frame.frameGeneration xor 1L)).isNull()
      assertThat(projector.hasPendingHistory).isTrue()
      val content = requireNotNull(projector.project(frame, publishedFrameGeneration = frame.frameGeneration))
      assertThat(content).isEqualTo(KetraTermOutputProjector().project(frame))
      assertThat(projector.hasPendingHistory).isFalse()
    }
  }
  private fun project(
    session: TerminalSession,
    projector: KetraTermOutputProjector,
    prioritizeVisible: Boolean = false,
  ): TerminalContentUpdatedEvent? {
    var result: TerminalContentUpdatedEvent? = null
    session.readRenderFrameForAbsoluteRange(0, Long.MAX_VALUE) { frame ->
      result = projector.project(frame, prioritizeVisible = prioritizeVisible)
    }
    return result
  }

  private fun assertProjectedPositions(
    session: TerminalSession,
    projector: KetraTermOutputProjector,
    actual: TerminalContentUpdatedEvent?,
  ) {
    val complete = requireNotNull(project(session, KetraTermOutputProjector()))
    assertThat(projector.cursor.logicalLineIndex).isEqualTo(complete.cursorLogicalLineIndex)
    assertThat(projector.cursor.columnIndex).isEqualTo(complete.cursorColumnIndex)
    if (actual != null) {
      assertThat(actual.cursorLogicalLineIndex).isEqualTo(complete.cursorLogicalLineIndex)
      assertThat(actual.cursorColumnIndex).isEqualTo(complete.cursorColumnIndex)
      assertThat(actual.screenTopLogicalLineIndex).isEqualTo(complete.screenTopLogicalLineIndex)
      assertThat(actual.screenTopColumnIndex).isEqualTo(complete.screenTopColumnIndex)
    }
  }

  private class MetadataCountingFrame(private val frame: TerminalRenderFrame) : TerminalRenderFrame by frame {
    var metadataReads = 0
      private set

    override fun lineId(row: Int): Long {
      metadataReads++
      return frame.lineId(row)
    }

    override fun lineGeneration(row: Int): Long {
      metadataReads++
      return frame.lineGeneration(row)
    }

    override fun lineWrapped(row: Int): Boolean {
      metadataReads++
      return frame.lineWrapped(row)
    }
  }

  private fun withSession(maxHistory: Int = 3, action: (TerminalSession, TestConnector, KetraTermOutputProjector) -> Unit) {
    val connector = TestConnector()
    val session = TerminalSession.create(TerminalBuffers.create(10, 3, maxHistory = maxHistory), connector)
    try {
      session.start(10, 3)
      action(session, connector, KetraTermOutputProjector())
    }
    finally {
      session.close()
    }
  }

  private class TestConnector : TerminalConnector {
    private lateinit var listener: TerminalConnectorListener

    override fun start(listener: TerminalConnectorListener) {
      this.listener = listener
    }

    fun feed(text: String) {
      val bytes = text.toByteArray(Charsets.UTF_8)
      listener.onBytes(bytes, 0, bytes.size)
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {}
    override fun resize(columns: Int, rows: Int) {}
    override fun close() {}
  }
}
