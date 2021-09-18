package com.intellij.ide.actions.searcheverywhere.ml.id

import java.util.concurrent.atomic.AtomicInteger

internal class SearchEverywhereMlItemIdProvider {
  private var idCounter = AtomicInteger(1)
  private val itemToId = hashMapOf<Any, Int>()

  fun isElementSupported(element: Any) = ElementKeyForIdProvider.isElementSupported(element)

  @Synchronized
  fun getId(element: Any): Int {
    val key = ElementKeyForIdProvider.getKey(element)
    return itemToId.computeIfAbsent(key) { idCounter.getAndIncrement() }
  }
}
