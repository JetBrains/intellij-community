package com.intellij.repository.search.completion.lookup

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class StrictOrderWeigher : LookupElementWeigher("strict-order", false, false) {
  override fun weigh(element: LookupElement): Comparable<*> {
    val data = element.getUserData(ORDER_KEY) ?: return Integer.MAX_VALUE
    if (data.source == DependencyCompletionContributionSource.SERVER) {
      return data.order
    }
    return (Integer.MAX_VALUE / 2) + data.order
  }
  companion object {
    val ORDER_KEY: Key<StrictOrderWeigherData> = Key.create("dependency.completion.order")
  }
}

@ApiStatus.Experimental
data class StrictOrderWeigherData(val source: DependencyCompletionContributionSource, val order: Int)