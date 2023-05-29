package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.util.gotoByName.GotoActionModel

class LocalSemanticActionsProvider(val model: GotoActionModel): SemanticActionsProvider() {
  override fun search(pattern: String): List<FoundItemDescriptor<GotoActionModel.MatchedValue>> {
    TODO("Not yet implemented")
  }
}