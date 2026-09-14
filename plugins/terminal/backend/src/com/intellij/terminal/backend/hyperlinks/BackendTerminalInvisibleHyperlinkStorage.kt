// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.backend.hyperlinks

import com.intellij.execution.filters.HyperlinkInfo
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkId

/**
 * Invisible hyperlinks found on hover, kept while the frontend still shows them.
 *
 * Not thread-safe, needs external synchronization.
 */
@ApiStatus.Internal
class BackendTerminalInvisibleHyperlinkStorage {
  private val hyperlinksById = HashMap<TerminalHyperlinkId, HyperlinkInfo>()
  private val hyperlinkIdsByRequestId = HashMap<Long, List<TerminalHyperlinkId>>()

  fun findHyperlink(hyperlinkId: TerminalHyperlinkId): HyperlinkInfo? = hyperlinksById[hyperlinkId]

  /**
   * Remembers the hyperlinks found by the request with [requestId].
   * A second result for the same request replaces the first.
   */
  fun addRequestResult(requestId: Long, hyperlinks: Map<TerminalHyperlinkId, HyperlinkInfo>) {
    removeRequest(requestId)
    if (hyperlinks.isEmpty()) return
    hyperlinkIdsByRequestId[requestId] = hyperlinks.keys.toList()
    hyperlinksById.putAll(hyperlinks)
  }

  /**
   * Forgets the hyperlinks of all requests except those with the given ids.
   */
  fun retainRequests(requestIds: Collection<Long>) {
    val retained = requestIds.toHashSet()
    for (requestId in hyperlinkIdsByRequestId.keys.toList()) {
      if (requestId !in retained) {
        removeRequest(requestId)
      }
    }
  }

  private fun removeRequest(requestId: Long) {
    val hyperlinkIds = hyperlinkIdsByRequestId.remove(requestId) ?: return
    for (hyperlinkId in hyperlinkIds) {
      hyperlinksById.remove(hyperlinkId)
    }
  }
}
