// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.hyperlinks

import com.intellij.execution.impl.EditorTextDecorationId
import org.jetbrains.plugins.terminal.hyperlinks.TerminalFilterResultInfo
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkId
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkInfo
import org.jetbrains.plugins.terminal.hyperlinks.toTerminalId
import java.util.concurrent.ConcurrentHashMap

/**
 * The ids of the file hyperlinks that the terminal shows now, the visible and the invisible ones.
 *
 * The processing of the hyperlinks adds and removes the ids on the UI thread. A menu update reads them on a
 * background thread, so the set is concurrent.
 */
internal class FrontendFileHyperlinkIds {
  private val ids: MutableSet<TerminalHyperlinkId> = ConcurrentHashMap.newKeySet()

  fun add(links: Collection<TerminalFilterResultInfo>) {
    for (link in links) {
      if (link is TerminalHyperlinkInfo && link.isFile) {
        ids.add(link.id)
      }
    }
  }

  fun remove(removed: Collection<TerminalHyperlinkId>) {
    ids.removeAll(removed.toSet())
  }

  fun removeDecorations(decorationIds: Collection<EditorTextDecorationId>) {
    remove(decorationIds.map { it.toTerminalId() })
  }

  operator fun contains(id: TerminalHyperlinkId): Boolean = id in ids
}
