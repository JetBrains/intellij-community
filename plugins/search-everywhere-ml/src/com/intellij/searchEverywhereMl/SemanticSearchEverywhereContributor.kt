package com.intellij.searchEverywhereMl

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SemanticSearchEverywhereContributor {
  fun isElementSemantic(element: Any): Boolean
}