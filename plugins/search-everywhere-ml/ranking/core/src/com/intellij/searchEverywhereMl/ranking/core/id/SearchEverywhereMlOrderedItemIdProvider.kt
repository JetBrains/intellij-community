package com.intellij.searchEverywhereMl.ranking.core.id

import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.searchEverywhereMl.ranking.ext.SearchEverywhereElementKeyProvider
import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.util.concurrent.atomic.AtomicInteger

internal interface SearchEverywhereMlItemIdProvider {
  fun getId(element: Any): Int?
}

/**
 * The ID provider computes the order-based key if such can be computed,
 * it means that the first element will have ID - 1, next different one 2, and so on.
 * If the key cannot be computed (due to unsupported element by any of the [SearchEverywhereElementKeyProvider] the id will be null.
 * @param onNullKey function executed when no key was computed for element. The element, for which there is no key, is passed as a parameter.
 */
@OptIn(IntellijInternalApi::class)
internal class SearchEverywhereMlOrderedItemIdProvider(private val onNullKey: (element: Any) -> Unit = {}) : SearchEverywhereMlItemIdProvider {
  private var idCounter = AtomicInteger(1)
  private val itemToId = hashMapOf<Any, Int>()


  @RequiresReadLock
  @Synchronized
  override fun getId(element: Any): Int? {
    val key = SearchEverywhereElementKeyProvider.getKeyOrNull(element)
    if (key == null) {
      onNullKey.invoke(element)
      return null
    }

    return itemToId.computeIfAbsent (key) { idCounter.getAndIncrement() }
  }
}
