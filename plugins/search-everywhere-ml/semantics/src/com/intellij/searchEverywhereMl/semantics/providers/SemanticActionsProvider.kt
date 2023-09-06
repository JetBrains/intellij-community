package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.ide.util.gotoByName.MatchMode
import com.intellij.openapi.actionSystem.ActionManager


abstract class SemanticActionsProvider(private val actionModel: GotoActionModel): StreamSemanticItemsProvider<GotoActionModel.MatchedValue> {
  private val actionManager = ActionManager.getInstance()

  protected fun createItemDescriptor(actionId: String,
                                     similarityScore: Double,
                                     pattern: String): FoundItemDescriptor<GotoActionModel.MatchedValue>? {
    val actionWrapper = actionManager.getAction(actionId)?.let {
      GotoActionModel.ActionWrapper(it, actionModel.getGroupMapping(it), MatchMode.NAME, actionModel)
    } ?: return null

    // Remove disabled actions from results:
    if (!actionWrapper.isAvailable) return null
    // actionWrapper.presentation.isEnabledAndVisible = true

    return FoundItemDescriptor(
      GotoActionModel.MatchedValue(actionWrapper, pattern, GotoActionModel.MatchedValueType.SEMANTIC, similarityScore),
      convertCosineSimilarityToInteger(similarityScore)
    )
  }
}