// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator.impl.ghostty

import com.intellij.terminal.emulator.TerminalSize
import com.intellij.terminal.emulator.createTerminalEmulator
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttySysLogLevel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList

class GhosttyLogTest {

  private data class RecordedLog(val level: GhosttySysLogLevel, val text: String)

  /**
   * Makes libghostty-vt actually logs a message, and assert we received it.
   */
  @Test
  fun capturesRealNativeLogFromLibghosttyVt() {
    assertThat(GhosttyLog.isLoggingEnabled()).isTrue()
    val recorded = CopyOnWriteArrayList<RecordedLog>()
    GhosttyLog.doWithHandler(GhosttyLog.Handler { level, scope, message ->
      recorded.add(RecordedLog(level, GhosttyLog.formatMessage(scope, message)))
    }) {
      createTerminalEmulator(TerminalSize(20, 5)).use { terminal ->
        // a zero-width character with no base character
        terminal.write(byteArrayOf(0xCC.toByte(), 0x81.toByte()))
      }
    }

    val warning = recorded.firstOrNull { it.text.contains("zero-width character") }
    assertThat(warning)
      .describedAs { "expected a native zero-width warning from libghostty-vt, got: $recorded" }
      .isNotNull()
    assertThat(warning!!.level).isSameAs(GhosttySysLogLevel.WARNING)
  }

  @Test
  fun logLevelMapping() {
    assertThat(GhosttySysLogLevel.of(0)).isSameAs(GhosttySysLogLevel.ERROR)
    assertThat(GhosttySysLogLevel.of(1)).isSameAs(GhosttySysLogLevel.WARNING)
    assertThat(GhosttySysLogLevel.of(2)).isSameAs(GhosttySysLogLevel.INFO)
    assertThat(GhosttySysLogLevel.of(3)).isSameAs(GhosttySysLogLevel.DEBUG)
    // Any unmodeled code is coerced to INFO.
    assertThat(GhosttySysLogLevel.of(42)).isSameAs(GhosttySysLogLevel.INFO)
    assertThat(GhosttySysLogLevel.of(-1)).isSameAs(GhosttySysLogLevel.INFO)
  }

  @Test
  fun formatMessageWithScope() {
    assertThat(GhosttyLog.formatMessage("osc", "bad sequence")).isEqualTo("[osc] bad sequence")
    assertThat(GhosttyLog.formatMessage("kitty", "")).isEqualTo("[kitty] ")
  }

  @Test
  fun formatMessageWithoutScope() {
    assertThat(GhosttyLog.formatMessage("", "unscoped message")).isEqualTo("unscoped message")
    assertThat(GhosttyLog.formatMessage("", "")).isEmpty()
  }

  @Test
  fun readUtf8DecodesBytes() {
    Arena.ofConfined().use { arena ->
      // A multibyte UTF-8 payload (kept as raw bytes to avoid non-ASCII source): 'e', then
      // U+00E9 (0xC3 0xA9, 2 bytes), a space, then U+20AC (0xE2 0x82 0xAC, 3 bytes).
      val bytes = byteArrayOf(
        0x65,
        0xC3.toByte(), 0xA9.toByte(),
        0x20,
        0xE2.toByte(), 0x82.toByte(), 0xAC.toByte(),
      )
      val seg = arena.allocate(bytes.size.toLong())
      MemorySegment.copy(bytes, 0, seg, ValueLayout.JAVA_BYTE, 0L, bytes.size)

      assertThat(GhosttyLog.readUtf8(seg, bytes.size.toLong())).isEqualTo(String(bytes, StandardCharsets.UTF_8))
      // A zero (or negative) length must decode to the empty string without touching the pointer.
      assertThat(GhosttyLog.readUtf8(seg, 0L)).isEmpty()
      assertThat(GhosttyLog.readUtf8(MemorySegment.NULL, 0L)).isEmpty()
    }
  }
}
