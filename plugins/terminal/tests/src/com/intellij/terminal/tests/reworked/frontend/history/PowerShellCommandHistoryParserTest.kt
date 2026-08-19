// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.history

import org.jetbrains.plugins.terminal.view.shellIntegration.history.PowerShellCommandHistoryParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PowerShellCommandHistoryParserTest {
  private val parser = PowerShellCommandHistoryParser()

  @Test
  fun `parses plain history entries in order`() {
    val history = "  Get-ChildItem\n\tSet-Location C:\\work\t\n\n"

    assertThat(parser.parse(history.toByteArray()))
      .containsExactly("Get-ChildItem", "Set-Location C:\\work")
  }

  @Test
  fun `joins lines ending with a continuation backtick`() {
    val history = listOf(
      "Write-Host \"first line`",
      "second line`",
      "third line\"",
      "Write-Host done",
    ).joinToString("\n")

    assertThat(parser.parse(history.toByteArray()))
      .containsExactly("Write-Host \"first line\\nsecond line\\nthird line\"", "Write-Host done")
  }

  @Test
  fun `removes explicit line continuation backticks`() {
    val history = listOf(
      "Get-ChildItem `",
      "  -Recurse `",
      "  -Filter '*.kt'",
    ).joinToString("\n")

    assertThat(parser.parse(history.toByteArray()))
      .containsExactly("Get-ChildItem \\n  -Recurse \\n  -Filter '*.kt'")
  }

  @Test
  fun `joins a pipeline only after a trailing continuation backtick`() {
    val history = listOf(
      "Get-ChildItem |`",
      "  Where-Object Name -like '*.kt'",
    ).joinToString("\n")

    assertThat(parser.parse(history.toByteArray()))
      .containsExactly("Get-ChildItem |\\n  Where-Object Name -like '*.kt'")
  }

  @Test
  fun `does not treat a backslash escaped backtick as a continuation`() {
    val history = "Get-Date \\`\nGet-Location"

    assertThat(parser.parse(history.toByteArray()))
      .containsExactly("Get-Date \\`", "Get-Location")
  }

  @Test
  fun `does not join lines inside quotes without a continuation backtick`() {
    val history = listOf(
      "Write-Host \"first line",
      "last line\"",
    ).joinToString("\n")

    assertThat(parser.parse(history.toByteArray()))
      .containsExactly("Write-Host \"first line", "last line\"")
  }

  @Test
  fun `parses utf16 history with byte order mark`() {
    val content = "Get-Date\nGet-Location".toByteArray(Charsets.UTF_16LE)
    val history = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + content

    assertThat(parser.parse(history)).containsExactly("Get-Date", "Get-Location")
  }

  @Test
  fun `decodes utf8 history with and without byte order mark`() {
    val content = "Write-Host Привет\nWrite-Host 世界".toByteArray(Charsets.UTF_8)
    val historyWithBom = UTF_8_BOM + content

    assertThat(parser.parse(content)).containsExactly("Write-Host Привет", "Write-Host 世界")
    assertThat(parser.parse(historyWithBom)).containsExactly("Write-Host Привет", "Write-Host 世界")
  }

  @Test
  fun `decodes utf16 big endian history with byte order mark`() {
    val content = "Get-Date\nGet-Location".toByteArray(Charsets.UTF_16BE)
    val history = UTF_16_BE_BOM + content

    assertThat(parser.parse(history)).containsExactly("Get-Date", "Get-Location")
  }

  @Test
  fun `parses crlf history`() {
    val history = "Get-Date\r\nGet-Location\r\n"

    assertThat(parser.parse(history.toByteArray())).containsExactly("Get-Date", "Get-Location")
  }

  @Test
  fun `ignores quotes after a comment while preserving hashes in quotes`() {
    val history = listOf(
      "Write-Host \"# is text\"",
      "Write-Host first # \"comment",
      "Get-Date",
    ).joinToString("\n")

    assertThat(parser.parse(history.toByteArray()))
      .containsExactly("Write-Host \"# is text\"", "Write-Host first # \"comment", "Get-Date")
  }

  @Test
  fun `keeps a hash inside a continued command as command text`() {
    val history = listOf(
      "Write-Host \"first line`",
      "# still part of the string`",
      "last line\"",
    ).joinToString("\n")

    assertThat(parser.parse(history.toByteArray()))
      .containsExactly("Write-Host \"first line\\n# still part of the string\\nlast line\"")
  }

  @Test
  fun `does not interpret escaped quotes or comments`() {
    val history = listOf(
      "Write-Host `\"quoted text",
      "Write-Host \"first line`",
      "second line\"",
      "Write-Host `# not a comment",
    ).joinToString("\n")

    assertThat(parser.parse(history.toByteArray()))
      .containsExactly(
        "Write-Host `\"quoted text",
        "Write-Host \"first line\\nsecond line\"",
        "Write-Host `# not a comment",
      )
  }

  private companion object {
    val UTF_8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    val UTF_16_BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
  }
}
