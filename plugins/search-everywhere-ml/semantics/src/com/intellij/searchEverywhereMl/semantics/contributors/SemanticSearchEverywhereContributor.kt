package com.intellij.searchEverywhereMl.semantics.contributors

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SemanticSearchEverywhereContributor {
  fun isElementSemantic(element: Any): Boolean
}