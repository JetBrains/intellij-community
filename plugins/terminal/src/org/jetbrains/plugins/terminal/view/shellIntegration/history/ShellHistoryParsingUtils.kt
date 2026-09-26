// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.shellIntegration.history

internal object ShellHistoryParsingUtils {
  fun normalizeHistoryCommand(command: String): String? {
    val trimmed = command.trimSpacesAndTabs()
    return trimmed.takeIf { it.isNotEmpty() }?.escapeControlCharacters()
  }

  fun findUnclosedQuote(
    line: String,
    initialQuote: Char?,
    commentCharacter: Char,
    escapeCharacter: Char,
  ): Char? {
    var quote = initialQuote
    var index = 0
    while (index < line.length) {
      val character = line[index]
      if (character == escapeCharacter) {
        index += 2
        continue
      }

      if (quote != null) {
        if (character == quote) {
          quote = null
        }
      }
      else {
        when (character) {
          commentCharacter -> return null
          '\'', '"' -> quote = character
        }
      }
      index++
    }
    return quote
  }

  fun hasLineContinuation(
    line: String,
    continuationCharacter: Char,
    escapeCharacter: Char = continuationCharacter,
  ): Boolean {
    if (line.lastOrNull() != continuationCharacter) return false

    val continuationIndex = line.lastIndex
    var escapeCharacterCount = 0
    for (index in continuationIndex - 1 downTo 0) {
      if (line[index] != escapeCharacter) break
      escapeCharacterCount++
    }
    return escapeCharacterCount % 2 == 0
  }

  private fun String.trimSpacesAndTabs(): String {
    var start = 0
    var end = length
    while (start < end && this[start].isSpaceOrTab()) {
      start++
    }
    while (end > start && this[end - 1].isSpaceOrTab()) {
      end--
    }
    return substring(start, end)
  }

  private fun Char.isSpaceOrTab(): Boolean = this == ' ' || this == '\t'

  private fun String.escapeControlCharacters(): String {
    return buildString(length) {
      for (character in this@escapeControlCharacters) {
        when (character) {
          '\n' -> append("\\n")
          '\r' -> append("\\r")
          '\t' -> append("\\t")
          '\b' -> append("\\b")
          '\u000C' -> append("\\f")
          else -> {
            if (character.isISOControl()) {
              append("\\u")
              append(character.code.toString(16).padStart(4, '0'))
            }
            else {
              append(character)
            }
          }
        }
      }
    }
  }
}
