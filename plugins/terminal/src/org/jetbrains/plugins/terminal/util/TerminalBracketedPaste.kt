// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package org.jetbrains.plugins.terminal.util

import org.jetbrains.annotations.ApiStatus

private const val BRACKETED_PASTE_START = "\u001b[200~"
private const val BRACKETED_PASTE_END = "\u001b[201~"

/**
 * Wraps [text] in the bracketed paste markers `ESC[200~` and `ESC[201~`.
 *
 * The function removes every start and end marker from [text] before the wrap.
 * Thus, the text cannot end the paste early, and no part of it reaches the program as keystrokes.
 * The removal also catches a marker that only appears after the removal of an inner marker.
 */
fun wrapInBracketedPaste(text: String): String {
  return BRACKETED_PASTE_START + removeBracketedPasteMarkers(text) + BRACKETED_PASTE_END
}

private fun removeBracketedPasteMarkers(text: String): String {
  if (!text.contains('\u001b')) {
    return text
  }
  // A marker in the result ends with the '~' that completes it, so a check at each '~' removes every marker.
  val result = StringBuilder(text.length)
  for (ch in text) {
    result.append(ch)
    if (ch == '~' && (result.endsWith(BRACKETED_PASTE_START) || result.endsWith(BRACKETED_PASTE_END))) {
      result.setLength(result.length - BRACKETED_PASTE_START.length)
    }
  }
  return result.toString()
}
