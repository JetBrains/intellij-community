// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.shellIntegration.history

import org.jetbrains.annotations.ApiStatus

/**
 * Parses the command-oriented history written by PSReadLine.
 *
 * UTF-8, UTF-8 with BOM, and UTF-16 files with a byte-order mark are supported. When saving history, PSReadLine
 * adds an unescaped trailing backtick to every continued command line, including lines continued by unclosed quotes,
 * pipeline characters, or other PowerShell syntax. The parser removes this marker and joins the line with the next
 * one. A backtick preceded by a backslash is not a continuation marker.
 *
 * The parser does not interpret quotes, pipeline characters, or other PowerShell syntax. The trailing backtick is the
 * only factor used to identify a continued command.
 */
@ApiStatus.Internal
class PowerShellCommandHistoryParser : ShellCommandHistoryParser {
  fun parse(content: ByteArray): List<String> = parse(content, Int.MAX_VALUE)

  override fun parse(content: ByteArray, commandLimit: Int): List<String> {
    val commands = LimitedHistoryCommands(commandLimit)
    val command = StringBuilder()

    for (rawLine in decode(content).lineSequence()) {
      val line = rawLine.removeSuffix("\r")
      appendLine(command, line)

      if (ShellHistoryParsingUtils.hasLineContinuation(line, '`', '\\')) {
        command.setLength(command.length - 1)
      }
      else {
        addCommand(commands, command)
      }
    }
    addCommand(commands, command)
    return commands.toList()
  }

  private fun appendLine(command: StringBuilder, line: String) {
    if (command.isNotEmpty()) {
      command.append('\n')
    }
    command.append(line)
  }

  private fun addCommand(commands: LimitedHistoryCommands, command: StringBuilder) {
    ShellHistoryParsingUtils.normalizeHistoryCommand(command.toString())?.let(commands::add)
    command.clear()
  }

  private fun decode(content: ByteArray): String {
    return when {
      content.startsWith(UTF_8_BOM) -> String(content, UTF_8_BOM.size, content.size - UTF_8_BOM.size, Charsets.UTF_8)
      content.startsWith(UTF_16_LE_BOM) -> String(content, UTF_16_LE_BOM.size, content.size - UTF_16_LE_BOM.size, Charsets.UTF_16LE)
      content.startsWith(UTF_16_BE_BOM) -> String(content, UTF_16_BE_BOM.size, content.size - UTF_16_BE_BOM.size, Charsets.UTF_16BE)
      else -> String(content, Charsets.UTF_8)
    }
  }

  private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

  private companion object {
    val UTF_8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    val UTF_16_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    val UTF_16_BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
  }
}
