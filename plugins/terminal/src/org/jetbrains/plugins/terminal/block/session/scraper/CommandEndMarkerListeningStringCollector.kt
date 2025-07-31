// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.session.scraper

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.session.NEW_LINE

@ApiStatus.Internal
class CommandEndMarkerListeningStringCollector(
  private val delegate: StringCollector,
  private val commandEndMarker: String?,
  private val onFound: () -> Unit,
) : StringCollector by delegate {

  override fun buildText(): String {
    val text = delegate.buildText()

    if (commandEndMarker == null) {
      return text
    }

    val trimmedText = text.trimEnd()
    if (trimmedText.endsWith(commandEndMarker)) {
      val outputText = trimmedText.dropLast(commandEndMarker.length)
      onFound()
      return outputText
    }

    // investigate why ConPTY inserts hard line breaks sometimes
    val indexOfSuffix = indexOfSuffix(trimmedText, commandEndMarker, ignoredCharacters = { it == NEW_LINE })
    if (indexOfSuffix >= 0) {
      val commandText = trimmedText.substring(0, indexOfSuffix)
      onFound()
      return commandText
    }

    return text
  }

  companion object {
    /**
     * Works like
     * ```kotlin
     * if text.endsWith(suffix)) {
     *   return text.lastIndexOf(suffix)
     * } else {
     *   return -1
     * }
     * ```
     * but ignores accidental appearance of [ignoredCharacters] in text
     *
     * @param ignoredCharacters characters that could appear in text and should be ignored
     * @return the index in [text] where [suffix] starts, or -1 if there is no such suffix
     */
    fun indexOfSuffix(text: String, suffix: String, ignoredCharacters: (Char) -> Boolean): Int {
      check(suffix.isNotEmpty())
      if (text.length < suffix.length) return -1
      var textInd: Int = text.length
      for (suffixInd in suffix.length - 1 downTo 0) {
        textInd--
        while (textInd >= 0 && ignoredCharacters(text[textInd])) {
          textInd--
        }
        if (textInd < 0 || text[textInd] != suffix[suffixInd]) {
          return -1
        }
      }
      return textInd
    }
  }

}
