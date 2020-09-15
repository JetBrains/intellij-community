package com.intellij.space.chat.ui

import com.intellij.space.chat.model.SpaceChatItem
import javax.swing.AbstractListModel
import kotlin.math.min

internal class SpaceChatItemListModel : AbstractListModel<SpaceChatItem>() {
  private val items = mutableListOf<SpaceChatItem>()

  override fun getElementAt(index: Int): SpaceChatItem = items[index]

  override fun getSize() = items.size

  fun messageListUpdated(newItems: List<SpaceChatItem>) {
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
}