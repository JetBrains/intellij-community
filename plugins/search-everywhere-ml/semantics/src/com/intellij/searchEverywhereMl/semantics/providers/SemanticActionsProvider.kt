package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.ide.util.gotoByName.MatchMode
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.searchEverywhereMl.semantics.settings.SearchEverywhereSemanticSettings


abstract class SemanticActionsProvider(private val actionModel: GotoActionModel,
                                       private val presentationProvider: suspend (AnAction) -> Presentation): StreamSemanticItemsProvider<GotoActionModel.MatchedValue> {
  private val actionManager = ActionManager.getInstance()

  internal var includeDisabledActions: Boolean = false

  override fun isEnabled(): Boolean = SearchEverywhereSemanticSettings.getInstance().enabledInActionsTab

  protected suspend fun createItemDescriptor(actionId: String,
                                             similarityScore: Double,
                                             pattern: String): FoundItemDescriptor<GotoActionModel.MatchedValue>? {
    val action = actionManager.getAction(actionId) ?: return null
    val actionWrapper = GotoActionModel.ActionWrapper(
      action, actionModel.getGroupMapping(action), MatchMode.NAME, actionModel,
      presentationProvider(action))

    // Remove disabled actions from results:
    if (!includeDisabledActions && !actionWrapper.isAvailable) return null

    return FoundItemDescriptor(
      GotoActionModel.MatchedValue(actionWrapper, pattern, GotoActionModel.MatchedValueType.SEMANTIC, similarityScore),
      convertCosineSimilarityToInteger(similarityScore)
    )
  }
}