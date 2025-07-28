// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.hyperlinks

import com.intellij.openapi.diagnostic.logger
import com.intellij.terminal.session.TerminalFilterResultInfo
import com.intellij.terminal.session.TerminalHyperlinkId
import com.intellij.terminal.session.TerminalHyperlinksModelState
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.TerminalOffset
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel

@ApiStatus.Internal
class TerminalHyperlinksModel(private val debugName: String, private val outputModel: TerminalOutputModel) {
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

  fun removeHyperlinks(fromAbsoluteOffset: Long): Collection<TerminalHyperlinkId> {
    if (hyperlinks.isEmpty()) return emptyList()
    val removedIds = mutableListOf<TerminalHyperlinkId>()
    removeTrimmedHyperlinks(removedIds)
    removeHyperlinksFromOffset(fromAbsoluteOffset, removedIds)
    logHyperlinksRemoved(fromAbsoluteOffset, removedIds)
    return removedIds
  }

  private fun removeTrimmedHyperlinks(removedIds: MutableList<TerminalHyperlinkId>) {
    // We use absoluteEndOffset here because the list is sorted by it,
    // so we can end up with a partially removed link, but that's OK, it'll be removed later.
    val trimOffset = outputModel.relativeOffset(0).toAbsolute()
    val removeUntilIndex = hyperlinks.binarySearch { it.absoluteEndOffset.compareTo(trimOffset) }.let {
      if (it >= 0) it + 1 else -it - 1
    }
    removeHyperlinksInRange(0, removeUntilIndex, removedIds)
  }

  private fun removeHyperlinksFromOffset(
    fromAbsoluteOffset: Long,
    removedIds: MutableList<TerminalHyperlinkId>,
  ) {
    // We use absoluteEndOffset here because if some link starts before the affected offset, but ends after it,
    // we still need to remove it to avoid a partially removed link that can overlap with new links.
    val removeFromIndex = hyperlinks.binarySearch { it.absoluteEndOffset.compareTo(fromAbsoluteOffset) }.let {
      if (it >= 0) it else -it - 1
    }
    removeHyperlinksInRange(removeFromIndex, hyperlinks.size, removedIds)
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

  fun dumpState(): TerminalHyperlinksModelState =
    TerminalHyperlinksModelState(hyperlinks.toList())

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
    fromAbsoluteOffset: Long,
    removedIds: List<TerminalHyperlinkId>,
  ) {
    if (!LOG.isDebugEnabled) return
    LOG.debug("$debugName Hyperlinks removed from offset $fromAbsoluteOffset (${fromAbsoluteOffset.toRelative()}) " +
      "and trimmed until ${outputModel.relativeOffset(0).toAbsolute()}: " +
      "removed IDs ${removedIds.minOfOrNull { it.value }}-${removedIds.maxOfOrNull { it.value }}, " +
      "now ${hyperlinks.size} links ${hyperlinks.loggableRange()}")
  }

  private fun List<TerminalFilterResultInfo>.loggableRange() =
    LoggableRange(
      firstOrNull()?.absoluteStartOffset?.addRelative(),
      lastOrNull()?.absoluteEndOffset?.addRelative(),
      minOfOrNull { it.id.value },
      maxOfOrNull { it.id.value },
    )

  private fun Long.toRelative(): Int = outputModel.absoluteOffset(this).toRelative()

  private fun Long.addRelative(): TerminalOffset = outputModel.absoluteOffset(this)

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
