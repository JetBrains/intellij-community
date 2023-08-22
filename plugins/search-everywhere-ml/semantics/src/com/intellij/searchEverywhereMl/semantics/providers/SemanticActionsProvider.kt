package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.ide.util.gotoByName.MatchMode
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.diagnostic.logger

private val LOG = logger<SemanticActionsProvider>()

abstract class SemanticActionsProvider: SemanticItemsProvider<GotoActionModel.MatchedValue> {
  private val actionManager = ActionManager.getInstance()

  protected fun createItemDescriptor(
    actionId: String, similarityScore: Double,
    pattern: String, model: GotoActionModel): FoundItemDescriptor<GotoActionModel.MatchedValue>? {

    val action: AnAction? = actionManager.getAction(actionId)
    if (action == null) {
      LOG.warn("Cannot find action by actionId: $actionId")
      return null
    }
    val wrapper = GotoActionModel.ActionWrapper(action, model.getGroupMapping(action), MatchMode.NAME, model)
    // wrapper.presentation.isEnabledAndVisible = true
    val element = GotoActionModel.MatchedValue(wrapper, pattern, GotoActionModel.MatchedValueType.SEMANTIC, similarityScore)

    // Remove disabled action from list
    if ((element.value as? GotoActionModel.ActionWrapper)?.isAvailable == false) {
      return null
    }

    val shiftedScore = MIN_WEIGHT + (
      similarityScore - MIN_SIMILARITY_SCORE) / (MAX_SIMILARITY_SCORE - MIN_SIMILARITY_SCORE) * (MAX_WEIGHT - MIN_WEIGHT)

    return FoundItemDescriptor(element, shiftedScore.toInt())
  }

  companion object {
    private const val MIN_SIMILARITY_SCORE = -1
    private const val MAX_SIMILARITY_SCORE = 1

    private const val MIN_WEIGHT = 0
    private const val MAX_WEIGHT = 100_000
  }
}