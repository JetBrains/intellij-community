package com.intellij.searchEverywhereMl.ranking.id

import com.intellij.openapi.application.ReadAction
import java.util.concurrent.atomic.AtomicInteger

internal interface SearchEverywhereMlItemIdProvider {
  fun getId(element: Any): Int?
}

/**
 * The ID provider computes the order-based key. It means that the first element will have ID-1, next different one - ID-2, and so on.
 * @param onNullKey function executed when no key was computed for element. The element, for which there is no key, is passed as a parameter.
 */
internal class SearchEverywhereMlOrderedItemIdProvider(private val onNullKey: (element: Any) -> Unit = {}) : SearchEverywhereMlItemIdProvider {
  private var idCounter = AtomicInteger(1)
  private val itemToId = hashMapOf<Any, Int>()

  @Synchronized
  override fun getId(element: Any): Int? {
    val key = ElementKeyForIdProvider.getKeyOrNull(element)
    if (key == null) {
      onNullKey.invoke(element)
      return null
    }

    return ReadAction.compute<Int?, Nothing> { itemToId.computeIfAbsent(key) { idCounter.getAndIncrement() } }
  }
}
