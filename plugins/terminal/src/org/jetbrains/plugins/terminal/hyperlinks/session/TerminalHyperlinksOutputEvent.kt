// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks.session

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
sealed interface TerminalHyperlinksOutputEvent {
  /**
   * A change in terminal hyperlinks: a self-contained update for a contiguous range of the output.
   *
   * The range `[coveredStartOffset, coveredEndOffset)` is one batch the highlighter has finished processing
   * against the snapshot at [documentModificationStamp]. [hyperlinks] are *exactly* the links found in that range
   * (possibly empty, meaning "no links here").
   */
  @Serializable
  data class HyperlinksUpdated(
    /**
     * The document modification stamp at the time a snapshot was taken to compute hyperlinks.
     */
    val documentModificationStamp: Long,
    /**
     * The absolute offset (document offset plus the trimmed count) at the start of the covered range, inclusive.
     */
    val coveredStartOffset: Long,
    /**
     * The absolute offset at the end of the covered range, exclusive.
     */
    val coveredEndOffset: Long,
    /**
     * The links found in `[coveredStartOffset, coveredEndOffset)`. May be empty.
     */
    val hyperlinks: List<TerminalFilterResultInfoDto>,
  ) : TerminalHyperlinksOutputEvent

  /**
   * Signals that a highlighting task has finished — every [HyperlinksUpdated] event
   * for that task has already been emitted before this one.
   */
  @Serializable
  data class TaskFinished(
    /** The document modification stamp of the task that just finished. */
    val documentModificationStamp: Long,
  ) : TerminalHyperlinksOutputEvent

  /**
   * Signals that [com.intellij.execution.filters.Filter]'s list used for hyperlink processing was updated.
   * So, the whole terminal output needs to be re-processed to add new hyperlinks and remove stale ones.
   * It is expected that the frontend should send the whole existing terminal output as
   * [TerminalHyperlinksInputEvent.ContentUpdated] again to re-process it.
   */
  @Serializable
  data object FiltersUpdated : TerminalHyperlinksOutputEvent
}