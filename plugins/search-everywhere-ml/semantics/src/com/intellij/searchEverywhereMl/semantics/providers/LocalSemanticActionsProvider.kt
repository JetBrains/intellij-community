package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.searchEverywhereMl.semantics.services.ActionEmbeddingsStorage

class LocalSemanticActionsProvider(val model: GotoActionModel) : SemanticActionsProvider() {
  override fun search(pattern: String): List<FoundItemDescriptor<GotoActionModel.MatchedValue>> {
    if (pattern.isBlank()) return emptyList()
    return ActionEmbeddingsStorage.getInstance().searchNeighbours(pattern, ITEMS_LIMIT, SIMILARITY_THRESHOLD).mapNotNull {
      createItemDescriptor(it.text, it.similarity, pattern, model)
    }
  }

  companion object {
    private const val ITEMS_LIMIT = 10
    private const val SIMILARITY_THRESHOLD = 0.5
  }
}