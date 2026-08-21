// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.session.jediterm

import com.jediterm.terminal.model.hyperlinks.HyperlinkFilter
import com.jediterm.terminal.model.hyperlinks.LinkResult
import com.jediterm.terminal.model.hyperlinks.LinkResultItem
import org.jetbrains.plugins.terminal.session.impl.JediTermOsc8LinkInfo

/**
 * A [HyperlinkFilter] installed on the Reworked terminal emulator so that JediTerm turns an OSC8
 * escape into a [com.jediterm.terminal.HyperlinkStyle] carrying the target URI.
 *
 * Unlike the Classic terminal's `Osc8UrlHyperlinkFilter`, this filter does not decide what is a link:
 * it captures every OSC8 target verbatim (spanning the whole URI, as required by
 * [com.jediterm.terminal.model.JediTerminal.setLinkUriStarted]). Whether the target is actually a
 * clickable link, and its navigation, is decided on the frontend where the URI is available as a
 * plain string (see `FrontendOsc8HyperlinksProcessing`).
 *
 * Note: [apply] may be called while the terminal text buffer lock is held, so it must not acquire any
 * other lock. It only allocates, so it is safe.
 */
internal class JediTermOsc8HyperlinkFilter : HyperlinkFilter {
  override fun apply(line: String): LinkResult? {
    if (line.isEmpty()) return null
    return LinkResult(LinkResultItem(0, line.length, JediTermOsc8LinkInfo(line)))
  }
}
