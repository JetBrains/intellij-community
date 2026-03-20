package com.intellij.repository.search.completion.lookup

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class StrictOrderWeigher : LookupElementWeigher("strict-order", false, false) {
  override fun weigh(element: LookupElement): Comparable<*> {
    return element.getUserData(ORDER_KEY) ?: 0
  }
  companion object {
    val ORDER_KEY: Key<Int> = Key.create("dependency.completion.order")
  }
}
