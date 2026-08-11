// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator.impl.ghostty

import com.intellij.terminal.emulator.BELL_CHAR
import com.intellij.terminal.emulator.ESC_STR
import com.intellij.terminal.emulator.OscTerminator
import com.intellij.terminal.emulator.TerminalCustomCommandListener
import com.intellij.terminal.emulator.csi
import com.intellij.terminal.emulator.esc
import com.intellij.terminal.emulator.osc
import com.intellij.terminal.emulator.session
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.charset.StandardCharsets
import java.util.concurrent.CancellationException

/** Tests OSC 1341 parsing directly and its integration with [com.intellij.terminal.emulator.TerminalEmulator.write]. */
internal class OscCustomCommandSnifferTest {

  // ---- happy paths ----

  @Test
  fun bellTerminated() {
    assertThat(sniff(osc("1341;foo;bar;baz"))).containsExactly(listOf("foo", "bar", "baz"))
  }

  @Test
  fun stTerminated() {
    assertThat(sniff(osc("1341;hello;world", OscTerminator.ST))).containsExactly(listOf("hello", "world"))
  }

  /** A `;` does introduce an argument, even when nothing follows it. */
  @ParameterizedTest
  @EnumSource(OscTerminator::class)
  fun emptyArguments(terminator: OscTerminator) {
    assertThat(sniff(osc("1341;", terminator))).describedAs("$terminator").containsExactly(listOf(""))
    assertThat(sniff(osc("1341;;", terminator))).containsExactly(listOf("", ""))
    assertThat(sniff(osc("1341;a;", terminator))).containsExactly(listOf("a", ""))
    assertThat(sniff(osc("1341;a;;b", terminator))).containsExactly(listOf("a", "", "b"))
  }

  @ParameterizedTest
  @EnumSource(OscTerminator::class)
  fun surroundingTextIsIgnored(terminator: OscTerminator) {
    val stream = "before" + osc("1341;a", terminator) + "between" + osc("1341;b", terminator) + "after"
    assertThat(sniff(stream)).containsExactly(listOf("a"), listOf("b"))
  }

  @Test
  fun severalCommandsAcrossFeeds() {
    assertThat(sniff(osc("1341;a"), osc("1341;b", OscTerminator.ST), osc("1341;c")))
      .containsExactly(listOf("a"), listOf("b"), listOf("c"))
  }

  @Test
  fun listenerExceptionDoesNotStopScanning() {
    val received = ArrayList<List<String>>()
    val sniffer = OscCustomCommandSniffer { command ->
      if (command == listOf("fails")) throw IllegalStateException("expected")
      received.add(command)
    }

    sniffer.feed((osc("1341;fails") + osc("1341;after")).toByteArray(StandardCharsets.UTF_8))

    assertThat(received).containsExactly(listOf("after"))
  }

  @Test
  fun listenerCancellationIsNotSwallowed() {
    val sniffer = OscCustomCommandSniffer { throw CancellationException("expected") }

    assertThrows<CancellationException> {
      sniffer.feed(osc("1341;command").toByteArray(StandardCharsets.UTF_8))
    }
  }

  /** A lone backslash is payload; only `ESC \` is a terminator. */
  @ParameterizedTest
  @EnumSource(OscTerminator::class)
  fun backslashInArgument(terminator: OscTerminator) {
    assertThat(sniff(osc("1341;C:\\Users\\test;n\\", terminator)))
      .containsExactly(listOf("C:\\Users\\test", "n\\"))
  }

  // ---- chunking: a PTY read can split a sequence at any byte ----

  @ParameterizedTest
  @EnumSource(OscTerminator::class)
  fun splitAtEveryOffset(terminator: OscTerminator) {
    val full = osc("1341;split;here", terminator)
    for (i in 0..full.length) {
      assertThat(sniff(full.substring(0, i), full.substring(i)))
        .describedAs("split at $i")
        .containsExactly(listOf("split", "here"))
    }
  }

  /** Verifies chunk boundaries inside multi-byte UTF-8 characters using [sniffBytes]. */
  @ParameterizedTest
  @EnumSource(OscTerminator::class)
  fun multiByteCharacterSplitAtEveryByte(terminator: OscTerminator) {
    val text = "caf" + Char(0xE9) + " " + Char(0x20AC) + " " + Char(0x201C) + "quoted" + Char(0x201D)
    val expected = listOf(text, "x")
    val full = osc("1341;$text;x", terminator).toByteArray(StandardCharsets.UTF_8)
    for (i in 0..full.size) {
      assertThat(sniffBytes(full.copyOfRange(0, i), full.copyOfRange(i, full.size)))
        .describedAs("split at byte $i of ${full.size}")
        .containsExactly(expected)
    }
    assertThat(sniffBytes(*Array(full.size) { byteArrayOf(full[it]) }))
      .describedAs("one byte per feed")
      .containsExactly(expected)
  }

  // ---- sequences that are not OSC 1341 ----

  @Test
  fun otherOscNumbersAreIgnored() {
    for (body in listOf("0;title", "2;title", "8;;https://example.com", "52;c;SGVsbG8=", "777;notify;t;b")) {
      assertThat(sniff(osc(body))).describedAs(body).isEmpty()
    }
  }

  @Test
  fun neighbouringOscNumbersAreIgnored() {
    for (body in listOf("134;a", "13410;a", "1341x;a", "01341;a", "1342;a", "13;41;a")) {
      assertThat(sniff(osc(body))).describedAs(body).isEmpty()
    }
  }

  @Test
  fun incompleteSequencesYieldNothing() {
    assertThat(sniff("$ESC_STR]1341;a")).isEmpty()  // never terminated
    assertThat(sniff("$ESC_STR]")).isEmpty()        // OSC opened, nothing else
    assertThat(sniff(ESC_STR)).isEmpty()            // dangling ESC
    assertThat(sniff("1341;a")).isEmpty()           // no introducer at all
  }

  /** A terminator before the `;`, including directly after `1341`, does not produce a command. */
  @ParameterizedTest
  @EnumSource(OscTerminator::class)
  fun aTerminatorBeforeTheSemicolonYieldsNothing(terminator: OscTerminator) {
    for (partial in listOf("", "1", "13", "134", "1341")) {
      assertThat(sniff(osc(partial, terminator))).describedAs("after `$partial`").isEmpty()
    }
  }

  /**
   * Such a terminator must also *clear* the half-matched prefix, not just decline to deliver. Otherwise
   * the match resumes across it and ordinary output that happens to continue the number — `1341;a` typed
   * after an empty OSC — is handed over as a command that was never introduced.
   */
  @ParameterizedTest
  @EnumSource(OscTerminator::class)
  fun aTerminatorClearsAHalfMatchedPrefix(terminator: OscTerminator) {
    for ((partial, suffix) in listOf("" to "1341;a", "13" to "41;b", "1341" to ";c")) {
      assertThat(sniff(osc(partial, terminator) + suffix + BELL_CHAR))
        .describedAs("after `$partial`")
        .isEmpty()
    }
    assertThat(sniff(osc("13", terminator) + osc("1341;ok"))).containsExactly(listOf("ok"))
  }

  @Test
  fun otherEscapeSequencesDoNotConfuseTheScanner() {
    val stream = csi("31m") + esc("=") + csi("2J") + osc("1341;a")
    assertThat(sniff(stream)).containsExactly(listOf("a"))
  }

  /** Consecutive ESCs stay armed, so the introducer that eventually follows still opens an OSC. */
  @Test
  fun repeatedEscapeStaysArmed() {
    assertThat(sniff("$ESC_STR$ESC_STR$ESC_STR]1341;x$BELL_CHAR")).containsExactly(listOf("x"))
  }

  // ---- a bare ESC abandons the sequence: only BEL and ST terminate one ----

  /** An ESC not followed by `\` abandons the current command. */
  @Test
  fun bareEscapeAbandonsTheSequence() {
    assertThat(sniff("$ESC_STR]1341;a" + esc("X"))).isEmpty()
    assertThat(sniff("$ESC_STR]1341;a" + csi("0m"))).isEmpty()
  }

  /** `ESC ]` abandons any partial OSC or prefix match and opens the next sequence. */
  @Test
  fun escapeIntroducerAbandonsAndOpensTheNextSequence() {
    for (abandonedBody in listOf("", "13", "1341", "1341;dropped;args", "52;c;SGVsbG8=")) {
      assertThat(sniff("$ESC_STR]$abandonedBody" + osc("1341;kept")))
        .describedAs("after `$abandonedBody`")
        .containsExactly(listOf("kept"))
    }
  }

  // ---- the argument cap ----

  @Test
  fun payloadAtTheByteLimitIsDelivered() {
    val atCap = "é".repeat(OscCustomCommandSniffer.MAX_BUFFER_BYTES / 2)
    assertThat(sniff(osc("1341;$atCap"))).containsExactly(listOf(atCap))
  }

  @Test
  fun payloadOneByteOverTheLimitIsDropped() {
    val overCap = "é".repeat(OscCustomCommandSniffer.MAX_BUFFER_BYTES / 2) + "a"
    assertThat(sniff(osc("1341;$overCap"))).isEmpty()
  }

  /** Overflow is per-sequence state: the command after an oversized one parses normally. */
  @Test
  fun scannerRecoversAfterOverflow() {
    val overCap = "a".repeat(OscCustomCommandSniffer.MAX_BUFFER_BYTES + 1)
    assertThat(sniff(osc("1341;$overCap"), osc("1341;after"))).containsExactly(listOf("after"))
  }

  /** After overflow resets the state, a later introducer still opens a command. */
  @Test
  fun anIntroducerInsideAnOversizedPayloadOpensANewCommand() {
    val overCap = "a".repeat(OscCustomCommandSniffer.MAX_BUFFER_BYTES + 1)
    assertThat(sniff(osc("1341;$overCap" + osc("1341;nested")))).containsExactly(listOf("nested"))
  }

  /** A large foreign OSC does not consume the custom-command buffer or affect the next command. */
  @Test
  fun largeForeignOscIsSkipped() {
    val huge = "A".repeat(4 * OscCustomCommandSniffer.MAX_BUFFER_BYTES)
    assertThat(sniff(osc("52;c;$huge"), osc("1341;a"))).containsExactly(listOf("a"))
  }

  // ---- end to end, through TerminalEmulator.write ----

  @Test
  fun deliveredThroughTheEmulator() = session(20, 3) { session ->
    val received = ArrayList<List<String>>()
    session.customCommandListener = TerminalCustomCommandListener { received.add(it) }

    session.write(osc("1341;foo;bar;baz"))                   // BEL-terminated
    session.write(osc("1341;hello;world", OscTerminator.ST)) // ST-terminated
    session.write(osc("1341;"))                              // one empty argument
    session.write(osc("1341"))                               // no `;`, so no command at all

    assertThat(received)
      .containsExactly(listOf("foo", "bar", "baz"), listOf("hello", "world"), listOf(""))
  }

  @Test
  fun splitAcrossEmulatorWrites() = session(20, 3) { session ->
    val received = ArrayList<List<String>>()
    session.customCommandListener = TerminalCustomCommandListener { received.add(it) }

    val full = osc("1341;split;here") // ESC ] 1341;split;here BEL
    for (ch in full) session.write(ch.toString())

    assertThat(received).containsExactly(listOf("split", "here"))
  }
}

/** Encodes each string separately; use [sniffBytes] for boundaries inside UTF-8 characters. */
private fun sniff(vararg chunks: String): List<List<String>> {
  return sniffBytes(*Array(chunks.size) { chunks[it].toByteArray(StandardCharsets.UTF_8) })
}

/** [sniff] over raw bytes, so a chunk boundary may fall anywhere — including inside a character. */
private fun sniffBytes(vararg chunks: ByteArray): List<List<String>> {
  val received = ArrayList<List<String>>()
  val sniffer = OscCustomCommandSniffer { received.add(it) }
  for (chunk in chunks) sniffer.feed(chunk)
  return received
}
