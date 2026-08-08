// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.session.impl

import com.jediterm.terminal.model.hyperlinks.LinkInfo
import org.jetbrains.annotations.ApiStatus

/**
 * A [LinkInfo] whose sole purpose is to carry the OSC8 hyperlink target [uri] through JediTerm's
 * [com.jediterm.terminal.HyperlinkStyle] so that the scraper can recover it later.
 *
 * The navigation itself is not performed here: in the Reworked terminal the link is resolved and
 * navigated on the frontend (see `FrontendOsc8HyperlinksProcessing`), so the JediTerm navigate
 * callback is a no-op.
 */
@ApiStatus.Internal
data class JediTermOsc8LinkInfo(val uri: String) : LinkInfo({})
