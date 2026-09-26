// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.shellIntegration.history

import org.jetbrains.annotations.ApiStatus

/**
 * Parses Zsh history in its plain and extended formats.
 *
 * Plain history stores one entry per line. Extended history prefixes the first line of an entry with
 * `: <epoch-seconds>:<duration-seconds>;`; the prefix is removed when it matches this format. Zsh metafies some bytes
 * in history files, so those bytes are restored before decoding the content as UTF-8.
 *
 * The only marker of a multiline command is a single trailing `\`. Zsh adds it for every line continuation, whether
 * the continuation is inside quotes or follows other shell syntax. Therefore, the parser joins the following line only
 * when the current line ends with one unescaped backslash; it does not need to parse quoting or other shell syntax.
 */
@ApiStatus.Internal
class ZshCommandHistoryParser : ShellCommandHistoryParser {
  fun parse(content: ByteArray): List<String> = parse(content, Int.MAX_VALUE)

  override fun parse(content: ByteArray, commandLimit: Int): List<String> {
    val history = String(unmetafy(content), Charsets.UTF_8)
    val commands = LimitedHistoryCommands(commandLimit)
    val command = StringBuilder()

    for (rawLine in history.lineSequence()) {
      val line = rawLine.removeSuffix("\r")
      if (command.isEmpty()) {
        command.append(EXTENDED_HISTORY_PREFIX.replaceFirst(line, ""))
      }
      else {
        command.append(line)
      }

      if (ShellHistoryParsingUtils.hasLineContinuation(line, '\\', '\\')) {
        command.setLength(command.length - 1)
        command.append('\n')
      }
      else {
        addCommand(commands, command)
      }
    }
    addCommand(commands, command)
    return commands.toList()
  }

  private fun addCommand(commands: LimitedHistoryCommands, command: StringBuilder) {
    if (command.isEmpty()) return

    ShellHistoryParsingUtils.normalizeHistoryCommand(command.toString())?.let(commands::add)
    command.clear()
  }

  private fun unmetafy(content: ByteArray): ByteArray {
    val result = ByteArray(content.size)
    var sourceIndex = 0
    var resultIndex = 0
    while (sourceIndex < content.size) {
      if (content[sourceIndex].toUByte() == META_BYTE && sourceIndex + 1 < content.size) {
        result[resultIndex++] = (content[sourceIndex + 1].toUByte().toInt() xor META_XOR).toByte()
        sourceIndex += 2
      }
      else {
        result[resultIndex++] = content[sourceIndex++]
      }
    }
    return result.copyOf(resultIndex)
  }

  private companion object {
    val EXTENDED_HISTORY_PREFIX = Regex("^: \\d+:\\d+;")
    const val META_BYTE: UByte = 0x83u
    const val META_XOR: Int = 0x20
  }
}
