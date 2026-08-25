// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import org.jetbrains.annotations.ApiStatus

/**
 * The IDE side of the contract with `PreviewClickGuard.js`.
 *
 * A preview script reports whether a click looked genuine; it cannot refuse the action itself, because
 * only the IDE can ask the user. Every privileged control in the preview - running a command, copying to
 * the clipboard, opening a browser - carries this flag so that one policy covers all of them instead of
 * each control inventing its own checks.
 */
@ApiStatus.Internal
object PreviewClickConfirmation {
  /** The value `clickGuard.confirmationFlag` produces when a click did not look genuine. */
  const val NEEDS_CONFIRMATION: String = "1"

  /**
   * Splits `"<flag>:<payload>"`, the layout used by messages whose payload is a single value, and returns
   * null when [data] does not have that shape. The command runner predates this helper and puts its flag
   * last, among several colon-separated fields, so it parses its own messages.
   */
  fun parseFlagPrefixed(data: String): Pair<Boolean, String>? {
    val separator = data.indexOf(':')
    if (separator < 0) {
      return null
    }
    return (data.substring(0, separator) == NEEDS_CONFIRMATION) to data.substring(separator + 1)
  }
}
