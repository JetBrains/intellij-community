// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui

import com.intellij.space.chat.model.api.SpaceChatItem
import javax.swing.AbstractListModel
import kotlin.math.min

internal class SpaceChatItemListModel : AbstractListModel<SpaceChatItem>() {
  private val items = mutableListOf<SpaceChatItem>()

  override fun getElementAt(index: Int): SpaceChatItem = items[index]

  override fun getSize() = items.size

  fun messageListUpdated(new: List<SpaceChatItem>) {
    val newItems = keepOldItemsState(new)
    val oldItems = ArrayList(items)

    items.clear()
    items.addAll(newItems)

    for (i in 0 until min(oldItems.size, newItems.size)) {
      if (oldItems[i] != newItems[i]) {
        fireContentsChanged(this, i, i)
      }
    }
    if (newItems.size < oldItems.size) {
      fireIntervalRemoved(this, newItems.size, oldItems.size - 1)
    }
    if (newItems.size > oldItems.size) {
      fireIntervalAdded(this, oldItems.size, newItems.size - 1)
    }
  }

  private fun keepOldItemsState(new: List<SpaceChatItem>): List<SpaceChatItem> {
    val oldItems = items.associateBy { it.id }
    return List(new.size) { i ->
      val newItem = new[i]
      val oldItem = oldItems[newItem.id]
      if (oldItem == newItem) {
        // if nothing changed, keep old reference
        oldItem
      }
      else {
        if (oldItem != null) {
          // if smth changed, keep loadingThread state
          newItem.loadingThread.value = oldItem.loadingThread.value
        }
        newItem
      }
    }
  }
}