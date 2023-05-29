package com.intellij.searchEverywhereMl.ranking.id

import java.util.concurrent.atomic.AtomicInteger

internal interface SearchEverywhereMlItemIdProvider {
  fun getId(element: Any): Int?
}

internal class SearchEverywhereMlOrderedItemIdProvider: SearchEverywhereMlItemIdProvider {
  private var idCounter = AtomicInteger(1)
  private val itemToId = hashMapOf<Any, Int>()

  @Synchronized
  override fun getId(element: Any): Int? {
    val key = ElementKeyForIdProvider.getKeyOrNull(element) ?: return null
    return itemToId.computeIfAbsent(key) { idCounter.getAndIncrement() }
  }
}
