// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.frontend.evolution.components

import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls

/** Width, in unscaled px, a tooltip line too long for the popup is wrapped to — see [multiLineTooltip]. */
private const val TOOLTIP_WRAP_WIDTH = 400

/** Roughly the characters [TOOLTIP_WRAP_WIDTH] holds; past this a line is worth wrapping. Approximate by design. */
private const val TOOLTIP_WRAP_CHARS = 70

/**
 * [text] as tooltip text that reads over several lines: its own `\n`s become breaks, and a line too long for the popup
 * is wrapped to [TOOLTIP_WRAP_WIDTH].
 *
 * Swing draws a tooltip as one single line unless the string itself begins with `<html>` — a `\n` in a plain one does
 * nothing at all — and what a tool reports here can be a sentence or two, uv explaining that it cannot read a
 * Poetry-style `requires-python` being the long one. Left as it came, such a tooltip runs off the edge of the screen,
 * which is where it stops being readable.
 */
internal fun multiLineTooltip(text: @Nls String): @NlsContexts.Tooltip String {
  // The split loses the @Nls of what it cut, so it is restated here rather than suppressed at each use below.
  val lines: List<@Nls String> = text.split('\n')
  val needsWrap = lines.any { it.length > TOOLTIP_WRAP_CHARS }
  // Nothing to lay out. Returned as-is rather than as an HTML rendering of itself: a one-line tooltip is not HTML, so
  // Swing would draw the escaping literally — an interpreter path containing `&` would read `&amp;`. The fixed width
  // would also pad such a tooltip out to the full box for nothing.
  if (!needsWrap && lines.size == 1) return text
  val body = HtmlBuilder().appendWithSeparators(HtmlChunk.br(), lines.map { HtmlChunk.text(it) }).toFragment()
  return (if (needsWrap) body.wrapWith(HtmlChunk.div().attr("width", JBUI.scale(TOOLTIP_WRAP_WIDTH))) else body)
    .wrapWith(HtmlChunk.html())
    .toString()
}
