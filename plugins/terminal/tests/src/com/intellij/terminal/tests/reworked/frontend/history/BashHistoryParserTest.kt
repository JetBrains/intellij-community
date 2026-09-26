// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.history

import org.jetbrains.plugins.terminal.view.shellIntegration.history.BashCommandHistoryParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class BashHistoryParserTest {
  private val parser = BashCommandHistoryParser()

  @Test
  fun `parses plain history entries in order`() {
    val history = "  git status\n\tcd ~/project\t\n\n# a shell comment\n"

    assertThat(parser.parse(history.toByteArray()))
      .containsExactly("git status", "cd ~/project", "# a shell comment")
  }

  @Test
  fun `parses mixed plain and timestamped history entries`() {
    val history = listOf(
      "plain command",
      "#1723456800",
      "git status",
      "#1723456815",
      "./gradlew test",
    ).joinToString("\n")

    assertThat(parser.parse(history.toByteArray()))
      .containsExactly("plain command", "git status", "./gradlew test")
  }

  @Test
  fun `joins lines inside quotes`() {
    val history = listOf(
      "#1723456800",
      "git commit -m \"first line",
      "second line\"",
      "git status",
    ).joinToString("\n")

    assertThat(parser.parse(history.toByteArray()))
      .containsExactly("git commit -m \"first line\\nsecond line\"", "git status")
  }

  @Test
  fun `keeps timestamp-like non-header lines as commands`() {
    val history = listOf(
      "#not-a-timestamp",
      "# 1723456800",
      "#1723456800 with text",
    ).joinToString("\n")

    assertThat(parser.parse(history.toByteArray()))
      .containsExactly("#not-a-timestamp", "# 1723456800", "#1723456800 with text")
  }

  @Test
  fun `decodes utf8 history`() {
    val history = "echo Привет\necho 世界"

    assertThat(parser.parse(history.toByteArray(Charsets.UTF_8)))
      .containsExactly("echo Привет", "echo 世界")
  }

  @Test
  fun `parses crlf timestamped history`() {
    val history = "#1723456800\r\ngit status\r\n#1723456815\r\n./gradlew test\r\n"

    assertThat(parser.parse(history.toByteArray()))
      .containsExactly("git status", "./gradlew test")
  }

  @Test
  fun `ignores quotes after a comment while preserving hashes in quotes`() {
    val history = listOf(
      "echo \"# is text\"",
      "echo first # \"comment",
      "echo second",
    ).joinToString("\n")

    assertThat(parser.parse(history.toByteArray()))
      .containsExactly("echo \"# is text\"", "echo first # \"comment", "echo second")
  }

  @Test
  fun `keeps a hash inside a multiline quote as command text`() {
    val history = listOf(
      "echo \"first line",
      "# still part of the string",
      "last line\"",
    ).joinToString("\n")

    assertThat(parser.parse(history.toByteArray()))
      .containsExactly("echo \"first line\\n# still part of the string\\nlast line\"")
  }

  @Test
  fun `does not treat escaped characters as quotes or comments`() {
    val history = listOf(
      "new command \\\" asds",
      "my new command \"1",
      "2",
      "3",
      "4",
      "5\"",
      "echo \\# not a comment",
    ).joinToString("\n")

    assertThat(parser.parse(history.toByteArray()))
      .containsExactly(
        "new command \\\" asds",
        "my new command \"1\\n2\\n3\\n4\\n5\"",
        "echo \\# not a comment",
      )
  }
}
