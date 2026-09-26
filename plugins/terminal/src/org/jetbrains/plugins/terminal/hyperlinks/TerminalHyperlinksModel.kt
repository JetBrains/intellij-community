// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks

import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.view.TerminalOffset

@ApiStatus.Internal
class TerminalHyperlinksModel(private val debugName: String) {
  // ordered by absoluteEndOffset because that's what removeHyperlinksFromOffset() needs
  private var hyperlinks: MutableList<TerminalFilterResultInfo> = mutableListOf()
  private val hyperlinksById = hashMapOf<TerminalHyperlinkId, TerminalFilterResultInfo>()

  fun getHyperlink(hyperlinkId: TerminalHyperlinkId): TerminalFilterResultInfo? = hyperlinksById[hyperlinkId]

  fun addHyperlinks(hyperlinks: List<TerminalFilterResultInfo>) {
    val newHyperlinks = hyperlinks.sortedBy { it.absoluteEndOffset }
    val oldHyperlinks = this.hyperlinks
    val allHyperlinks = ArrayList<TerminalFilterResultInfo>(oldHyperlinks.size + newHyperlinks.size)
    var i = 0
    var j = 0
    while (i < oldHyperlinks.size || j < newHyperlinks.size) {
      val takeOld = when {
        i >= oldHyperlinks.size -> false
        j >= newHyperlinks.size -> true
        else -> oldHyperlinks[i].absoluteEndOffset <= newHyperlinks[j].absoluteEndOffset
      }
      if (takeOld) {
        allHyperlinks += oldHyperlinks[i++]
      }
      else {
        val newHyperlink = newHyperlinks[j++]
        allHyperlinks += newHyperlink
        hyperlinksById[newHyperlink.id] = newHyperlink
      }
    }
    logHyperlinksAdded(this.hyperlinks, newHyperlinks, allHyperlinks)
    this.hyperlinks = allHyperlinks
  }

  /**
   * Removes hyperlinks whose end offset falls into `[fromAbsoluteOffset, toAbsoluteOffset]`,
   * as well as any hyperlinks trimmed from the start of the output.
   * Also, removes all hyperlinks that end before the [trimUntilOffset].
   */
  fun removeHyperlinks(
    fromAbsoluteOffset: Long,
    toAbsoluteOffset: Long = Long.MAX_VALUE,
    trimUntilOffset: Long = 0,
  ): Collection<TerminalHyperlinkId> {
    if (hyperlinks.isEmpty()) return emptyList()
    val removedIds = mutableListOf<TerminalHyperlinkId>()
    removeTrimmedHyperlinks(trimUntilOffset, removedIds)
    removeHyperlinksInOffsetRange(fromAbsoluteOffset, toAbsoluteOffset, removedIds)
    logHyperlinksRemoved(trimUntilOffset, fromAbsoluteOffset, removedIds)
    return removedIds
  }

  private fun removeTrimmedHyperlinks(trimUntilOffset: Long, removedIds: MutableList<TerminalHyperlinkId>) {
    // We use absoluteEndOffset here because the list is sorted by it,
    // so we can end up with a partially removed link, but that's OK, it'll be removed later.
    val removeUntilIndex = hyperlinks.binarySearch { it.absoluteEndOffset.compareTo(trimUntilOffset) }.let {
      if (it >= 0) it + 1 else -it - 1
    }
    removeHyperlinksInRange(0, removeUntilIndex, removedIds)
  }

  private fun removeHyperlinksInOffsetRange(
    fromAbsoluteOffset: Long,
    toAbsoluteOffset: Long,
    removedIds: MutableList<TerminalHyperlinkId>,
  ) {
    // The list is sorted by absoluteEndOffset.
    // We use absoluteEndOffset here because if some link starts before the affected offset, but ends after it,
    // we still need to remove it to avoid a partially removed link that can overlap with new links.
    // Lower bound (inclusive): the first link whose end offset reaches fromAbsoluteOffset.
    val removeFromIndex = hyperlinks.binarySearch {
      if (it.absoluteEndOffset < fromAbsoluteOffset) -1 else 1
    }.let { -it - 1 }
    // Upper bound (exclusive): the first link whose end offset is past toAbsoluteOffset.
    val removeUntilIndex = hyperlinks.binarySearch {
      if (it.absoluteEndOffset <= toAbsoluteOffset) -1 else 1
    }.let { -it - 1 }
    if (removeFromIndex < removeUntilIndex) {
      removeHyperlinksInRange(removeFromIndex, removeUntilIndex, removedIds)
    }
  }

  private fun removeHyperlinksInRange(
    removeFromIndex: Int,
    removeUntilIndex: Int,
    removedIds: MutableList<TerminalHyperlinkId>,
  ) {
    val toRemove = hyperlinks.subList(removeFromIndex, removeUntilIndex)
    for (hyperlinkInfo in toRemove) {
      hyperlinksById.remove(hyperlinkInfo.id)
      removedIds += hyperlinkInfo.id
    }
    toRemove.clear()
  }

  private fun logHyperlinksAdded(
    previousHyperlinks: List<TerminalFilterResultInfo>,
    addedHyperlinks: List<TerminalFilterResultInfo>,
    modifiedHyperlinks: List<TerminalFilterResultInfo>,
  ) {
    if (!LOG.isDebugEnabled) return
    LOG.debug("$debugName Hyperlinks added: " +
              "previously ${previousHyperlinks.size} links ${previousHyperlinks.loggableRange()}, " +
              "added ${addedHyperlinks.size} links ${addedHyperlinks.loggableRange()}, " +
              "now ${modifiedHyperlinks.size} links ${modifiedHyperlinks.loggableRange()}")
  }

  private fun logHyperlinksRemoved(
    trimUntilOffset: Long,
    fromAbsoluteOffset: Long,
    removedIds: List<TerminalHyperlinkId>,
  ) {
    if (!LOG.isDebugEnabled) return
    LOG.debug("$debugName Hyperlinks removed from offset $fromAbsoluteOffset " +
              "and trimmed until $trimUntilOffset: " +
              "removed IDs ${removedIds.minOfOrNull { it.value }}-${removedIds.maxOfOrNull { it.value }}, " +
              "now ${hyperlinks.size} links ${hyperlinks.loggableRange()}")
  }

  private fun List<TerminalFilterResultInfo>.loggableRange() =
    LoggableRange(
      firstOrNull()?.absoluteStartOffset?.toTerminalOffset(),
      lastOrNull()?.absoluteEndOffset?.toTerminalOffset(),
      minOfOrNull { it.id.value },
      maxOfOrNull { it.id.value },
    )

  private fun Long.toTerminalOffset(): TerminalOffset = TerminalOffset.of(this)

}

private data class LoggableRange(
  val fromOffset: TerminalOffset?,
  val toOffset: TerminalOffset?,
  val minId: Long?,
  val maxId: Long?,
) {
  override fun toString(): String = "$fromOffset-$toOffset (IDs $minId-$maxId)"
}

private val LOG = logger<TerminalHyperlinksModel>()
