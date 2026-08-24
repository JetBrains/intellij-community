// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.history

import org.jetbrains.plugins.terminal.view.shellIntegration.history.ZshCommandHistoryParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ZshHistoryParserTest {
  private val parser = ZshCommandHistoryParser()

  @Test
  fun `parses plain history entries in order`() {
    val history = "  git status\n\tcd ~/project\t\n\n echo \"  quoted text  \"  \n"

    assertThat(parser.parse(history.toByteArray()))
      .containsExactly("git status", "cd ~/project", "echo \"  quoted text  \"")
  }

  @Test
  fun `parses mixed plain and extended history entries`() {
    val history = listOf(
      "plain command",
      ": 1723456800:0;git status",
      "another plain command",
      ": 1723456815:15;./gradlew test",
    ).joinToString("\n")

    assertThat(parser.parse(history.toByteArray()))
      .containsExactly("plain command", "git status", "another plain command", "./gradlew test")
  }

  @Test
  fun `keeps a malformed extended history prefix as command text`() {
    val history = listOf(
      ": not-a-timestamp:0;echo first",
      ": 1723456800:not-a-duration;echo second",
    ).joinToString("\n")

    assertThat(parser.parse(history.toByteArray()))
      .containsExactly(": not-a-timestamp:0;echo first", ": 1723456800:not-a-duration;echo second")
  }

  @Test
  fun `preserves a timestamp-looking continuation line as command text`() {
    val history = listOf(
      ": 1723456800:0;echo first line \\",
      ": 1723456815:15;echo second line",
    ).joinToString("\n")

    assertThat(parser.parse(history.toByteArray()))
      .containsExactly("echo first line \\n: 1723456815:15;echo second line")
  }

  @Test
  fun `joins lines after a trailing backslash`() {
    val history = "echo first \\" + "\nsecond line"

    assertThat(parser.parse(history.toByteArray()))
      .containsExactly("echo first \\nsecond line")
  }

  @Test
  fun `does not treat an escaped trailing backslash as a continuation`() {
    val history = "echo two backslashes " + "\\\\" + "\nnext command"

    assertThat(parser.parse(history.toByteArray()))
      .containsExactly("echo two backslashes " + "\\\\", "next command")
  }

  @Test
  fun `escapes control characters inside commands`() {
    val history = "printf 'first\tsecond'\n\tcommand\u000Cwith-form-feed\t"

    assertThat(parser.parse(history.toByteArray()))
      .containsExactly("printf 'first\\tsecond'", "command\\fwith-form-feed")
  }

  @Test
  fun `unmetafies zsh history before decoding utf8`() {
    val history = byteArrayOf(
      'e'.code.toByte(),
      'c'.code.toByte(),
      'h'.code.toByte(),
      'o'.code.toByte(),
      ' '.code.toByte(),
      0x83.toByte(),
      (0xC3 xor 0x20).toByte(),
      0x83.toByte(),
      (0xA9 xor 0x20).toByte(),
    )

    assertThat(parser.parse(history)).containsExactly("echo é")
  }

  @Test
  fun `decodes utf8 history`() {
    val history = "echo Привет\necho 世界"

    assertThat(parser.parse(history.toByteArray(Charsets.UTF_8)))
      .containsExactly("echo Привет", "echo 世界")
  }

  @Test
  fun `parses crlf extended history`() {
    val history = ": 1723456800:0;git status\r\n: 1723456815:15;./gradlew test\r\n"

    assertThat(parser.parse(history.toByteArray()))
      .containsExactly("git status", "./gradlew test")
  }

  @Test
  fun `treats a trailing backslash in a comment as a continuation`() {
    val history = "# comment \\" + "\nnext line"

    assertThat(parser.parse(history.toByteArray()))
      .containsExactly("# comment \\nnext line")
  }
}
