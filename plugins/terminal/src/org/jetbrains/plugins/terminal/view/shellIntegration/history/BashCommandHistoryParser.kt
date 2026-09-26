// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.shellIntegration.history

import org.jetbrains.annotations.ApiStatus

/**
 * Parses Bash history in both plain and timestamped formats.
 *
 * With `HISTTIMEFORMAT` enabled, Bash writes a timestamp as a separate `#<epoch-seconds>` line before a command.
 * Such lines are skipped only between commands; a timestamp-looking line inside a multiline command remains command text.
 * Bash itself folds commands continued by shell syntax into one history line, so the parser receives an already joined
 * entry. It treats an entry as multiline only when a single or double quote is unclosed, then joins lines until the
 * quote is closed. Backslash escapes and comments are considered while looking for the closing quote.
 *
 * Both plain history entries and timestamped entries are supported.
 */
@ApiStatus.Internal
class BashCommandHistoryParser : ShellCommandHistoryParser {
  fun parse(content: ByteArray): List<String> = parse(content, Int.MAX_VALUE)

  override fun parse(content: ByteArray, commandLimit: Int): List<String> {
    val commands = LimitedHistoryCommands(commandLimit)
    val command = StringBuilder()
    var quote: Char? = null

    for (rawLine in String(content, Charsets.UTF_8).lineSequence()) {
      val line = rawLine.removeSuffix("\r")
      if (BASH_TIMESTAMP.matches(line) && command.isEmpty()) {
        continue
      }

      if (command.isNotEmpty()) {
        command.append('\n')
      }
      command.append(line)
      quote = ShellHistoryParsingUtils.findUnclosedQuote(line, quote, '#', '\\')
      if (quote == null) {
        addCommand(commands, command)
      }
    }
    addCommand(commands, command)
    return commands.toList()
  }

  private fun addCommand(commands: LimitedHistoryCommands, command: StringBuilder) {
    ShellHistoryParsingUtils.normalizeHistoryCommand(command.toString())?.let(commands::add)
    command.clear()
  }

  private companion object {
    val BASH_TIMESTAMP = Regex("^#\\d+$")
  }
}
