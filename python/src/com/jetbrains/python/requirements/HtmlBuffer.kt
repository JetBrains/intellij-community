// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

/**
 * HTML string builder with a compile-visible escape boundary — [text] escapes XML entities,
 * [raw] appends verbatim. Splitting the two APIs stops the double-escape / missed-escape
 * mistake Ilya's PY-89838 review flagged: at every call site the caller has to pick which
 * method to invoke, so the escaping story is explicit at every point instead of relying on
 * convention.
 *
 * `toString` is the read side used by the documentation provider — it must return the
 * accumulated HTML, not the default `Object` identity string. That single method is what
 * PY-91067 exercised: before the override existed, the Quick Doc tooltip printed
 * `HtmlBuffer@<hash>` verbatim.
 */
internal class HtmlBuffer {
  private val buffer = StringBuilder()

  fun text(value: String): HtmlBuffer {
    buffer.append(value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
    return this
  }

  fun raw(value: String): HtmlBuffer {
    buffer.append(value)
    return this
  }

  override fun toString(): String = buffer.toString()
}
