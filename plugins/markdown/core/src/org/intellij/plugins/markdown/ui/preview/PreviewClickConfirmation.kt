// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import org.jetbrains.annotations.ApiStatus

/**
 * IDE side of the contract with `PreviewClickGuard.js`: the page reports whether a click looked genuine,
 * and only the IDE can act on it by asking the user. One policy for every privileged preview control.
 */
@ApiStatus.Internal
object PreviewClickConfirmation {
  const val GENUINE_CLICK: String = "0"

  const val HIJACKED_CLICK: String = "1"

  /** Fails closed: only an explicit [GENUINE_CLICK] suppresses the question. */
  fun needsConfirmation(flag: String?): Boolean = flag != GENUINE_CLICK

  /**
   * Splits `"<flag>:<payload>"`, or null if the prefix is not a known flag - a payload has colons of its
   * own, so accepting an unknown prefix would yield a truncated payload instead of failing.
   */
  fun parseFlagPrefixed(data: String): Pair<Boolean, String>? {
    val separator = data.indexOf(':')
    if (separator < 0) {
      return null
    }
    val flag = data.substring(0, separator)
    if (flag != GENUINE_CLICK && flag != HIJACKED_CLICK) {
      return null
    }
    return needsConfirmation(flag) to data.substring(separator + 1)
  }
}
