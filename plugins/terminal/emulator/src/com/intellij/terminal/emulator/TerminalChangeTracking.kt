// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.jetbrains.annotations.ApiStatus

// What changed since the last frame, for the two renderer models: repainting a fixed viewport
// ([ScreenChange]) and appending scrolled-off lines to a growing document ([HistoryMark]). Part of the
// backend-agnostic API; see TerminalEmulator.kt.

/**
 * Active-screen rows that changed since the previous [TerminalEmulator.takeChanges] call.
 *
 * Reported at row granularity where the engine supports it; engines that cannot track individual
 * rows report [All] whenever anything changed. Scrollback growth is observed via
 * [TerminalEmulator.scrollbackRows], not here (there is no per-scrollback-line change tracking).
 */
@ApiStatus.Internal
sealed interface ScreenChange {
  /** Nothing changed; the previous frame is still valid. */
  data object None : ScreenChange

  /** The whole active screen must be redrawn. */
  data object All : ScreenChange

  /** Only these active-screen row indices changed. */
  class Rows(rows: IntArray) : ScreenChange {
    private val backing: IntArray = rows.copyOf()

    /** The changed row indices (a defensive copy). */
    val rows: IntArray get() = backing.copyOf()

    override fun equals(other: Any?): Boolean = this === other || (other is Rows && backing.contentEquals(other.backing))
    override fun hashCode(): Int = backing.contentHashCode()
    override fun toString(): String = "Rows(${backing.contentToString()})"
  }
}

/**
 * A movable mark at the boundary between finalized scrollback history and the live active screen, for
 * renderers that *append* scrolled-off lines to a growing document (e.g. an editor) rather than
 * repainting a fixed viewport. It complements [ScreenChange]/[TerminalEmulator.takeChanges], which
 * reports the live viewport as fully changed on every scroll — the wrong signal for an append model.
 *
 * A scroll pushes the top active-screen line into history; [finalizedLineCount] reports how many such
 * lines have accumulated since the mark was created or last [reset]. Those are the newest
 * [finalizedLineCount] scrollback lines, so a consumer does:
 *
 * ```
 * val n = mark.finalizedLineCount()
 * for (i in emulator.scrollbackRows - n until emulator.scrollbackRows) appendToDocument(emulator.scrollbackLine(i))
 * mark.reset()
 * ```
 *
 * The count stays exact even after the scrollback byte cap begins evicting the oldest lines — where a
 * raw [TerminalEmulator.scrollbackRows] delta plateaus and under-counts — because the mark tracks the
 * content itself, not an index. It fails only ([finalizedLineCount] returns `-1`) if the marked
 * boundary is itself evicted, i.e. more than the whole retained scrollback scrolled past between two
 * [reset]s; the caller must then fall back to a full re-sync of the visible screen.
 *
 * Obtain one from [TerminalEmulator.markHistoryBoundary]; [close] it when done (it holds a native
 * resource). Meant to be long-lived — typically one per renderer.
 */
@ApiStatus.Internal
interface HistoryMark : AutoCloseable {
  /**
   * The number of lines finalized into scrollback (scrolled above the active screen) since this mark
   * was created or last [reset], or `-1` if the marked boundary has itself been evicted from the
   * bounded scrollback (the caller must then re-sync fully). Read-only: does not move the mark.
   */
  fun finalizedLineCount(): Int

  /**
   * Re-anchors the mark to the current history / active-screen boundary, so the next
   * [finalizedLineCount] counts from here. Call after consuming the finalized lines.
   */
  fun reset()

  /** Releases the native tracked reference. Idempotent. */
  override fun close()
}
