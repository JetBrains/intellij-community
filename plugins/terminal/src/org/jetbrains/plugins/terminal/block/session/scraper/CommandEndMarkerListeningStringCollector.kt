// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.session.scraper

import org.jetbrains.plugins.terminal.block.session.NEW_LINE

internal class CommandEndMarkerListeningStringCollector(
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
    val suffixStartInd = findSuffixStartIndIgnoringLF(trimmedText, commandEndMarker)
    if (suffixStartInd >= 0) {
      val commandText = trimmedText.substring(0, suffixStartInd)
      onFound()
      return commandText
    }

    return text
  }

  /**
   * @return the index in [text] where [suffix] starts, or -1 if there is no such suffix
   */
  private fun findSuffixStartIndIgnoringLF(text: String, suffix: String): Int {
    check(suffix.isNotEmpty())
    if (text.length < suffix.length) return -1
    var textInd: Int = text.length
    for (suffixInd in suffix.length - 1 downTo 0) {
      textInd--
      while (textInd >= 0 && text[textInd] == NEW_LINE) {
        textInd--
      }
      if (textInd < 0 || text[textInd] != suffix[suffixInd]) {
        return -1
      }
    }
    return textInd
  }

}