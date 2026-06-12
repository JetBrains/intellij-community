// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.progress

import com.intellij.openapi.progress.util.ProgressBarUtil
import org.jetbrains.plugins.terminal.progress.TerminalProgressParser
import org.jetbrains.plugins.terminal.progress.TerminalProgressState
import org.jetbrains.plugins.terminal.progress.TerminalProgressStatus
import org.jetbrains.plugins.terminal.progress.progressBarStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalProgressParserTest {
  @Test
  fun `parses determinate progress terminated by BEL`() {
    val events = parse("$ESC]9;4;1;42$BEL")

    assertEquals(listOf(TerminalProgressState.normal(42)), events)
  }

  @Test
  fun `parses indeterminate progress terminated by ST`() {
    val events = parse("$ESC]9;4;3;0$ESC\\")

    assertEquals(listOf(TerminalProgressState.indeterminate()), events)
  }

  @Test
  fun `parses clear progress without percent`() {
    val events = parse("$ESC]9;4;0$BEL")

    assertEquals(listOf(TerminalProgressState.NONE), events)
  }

  @Test
  fun `parses split sequence`() {
    val events = mutableListOf<TerminalProgressState>()
    val parser = TerminalProgressParser(events::add)

    parser.process("${ESC}]9;")
    parser.process("4;4;7")
    parser.process("5$BEL")

    assertEquals(listOf(TerminalProgressState.warning(75)), events)
  }

  @Test
  fun `parses error state`() {
    val events = parse("$ESC]9;4;2;13$BEL")

    assertEquals(TerminalProgressStatus.ERROR, events.single().status)
    assertEquals(13, events.single().percent)
  }

  @Test
  fun `maps terminal progress states to progress bar statuses`() {
    assertEquals(ProgressBarUtil.FAILED_VALUE, TerminalProgressState.error(15).progressBarStatus)
    assertEquals(ProgressBarUtil.WARNING_VALUE, TerminalProgressState.warning(15).progressBarStatus)
    assertNull(TerminalProgressState.normal(15).progressBarStatus)
    assertNull(TerminalProgressState.indeterminate().progressBarStatus)
    assertNull(TerminalProgressState.NONE.progressBarStatus)
  }

  @Test
  fun `ignores unsupported and malformed sequences`() {
    val events = parse("plain text$ESC]0;title$BEL$ESC]9;4;1;101$BEL$ESC]9;4;x;1$BEL")

    assertTrue(events.isEmpty())
  }

  private fun parse(text: String): List<TerminalProgressState> {
    val events = mutableListOf<TerminalProgressState>()
    TerminalProgressParser(events::add).process(text)
    return events
  }

  private companion object {
    const val ESC: Char = '\u001B'
    const val BEL: Char = '\u0007'
  }
}
