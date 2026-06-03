// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks.session

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
sealed interface TerminalHyperlinksOutputEvent {
  /**
   * A change in terminal hyperlinks.
   *
   * If there are a lot of links, they may arrive in batches with the same events having the same [documentModificationStamp].
   * In this case, only the first event of a batch will have this property set.
   * When the model receives the first event, it removes the old hyperlinks from that offset onwards.
   * The next events will only add new hyperlinks to the model.
   * The last event always has an empty hyperlink list and used to indicate that the hyperlink processing has finished.
   */
  @Serializable
  data class HyperlinksUpdated(
    /**
     * The document modification stamp at the time a snapshot was taken to compute hyperlinks.
     */
    val documentModificationStamp: Long,
    /**
     * The absolute offset (document offset plus the trimmed count) from which the links were updated.
     *
     * Only set for the first event in a batch.
     */
    val removeFromOffset: Long?,
    /**
     * The newly computed hyperlinks.
     *
     * May be empty for the first event in a batch, always empty for the last event.
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