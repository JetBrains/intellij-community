// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package org.jetbrains.plugins.terminal.util

import org.jetbrains.annotations.ApiStatus

private const val BRACKETED_PASTE_START = "\u001b[200~"
private const val BRACKETED_PASTE_END = "\u001b[201~"

/**
 * Wraps [text] in the bracketed paste markers `ESC[200~` and `ESC[201~`.
 *
 * The function replaces every ESC in [text] with the visible symbol `␛` (U+241B).
 * A text without ESC cannot contain the end marker, so it cannot end the paste early and run a command.
 * The user sees `␛` in the pasted text and knows that the copied text has a control character.
 *
 * A pasted text is not expected to contain escape sequences, so the function has no special support for them.
 * If a real case needs them, revisit this decision.
 *
 * See [IJPL-260504](https://youtrack.jetbrains.com/issue/IJPL-260504).
 */
fun wrapInBracketedPaste(text: String): String {
  return BRACKETED_PASTE_START + text.replace('\u001b', '␛') + BRACKETED_PASTE_END
}
