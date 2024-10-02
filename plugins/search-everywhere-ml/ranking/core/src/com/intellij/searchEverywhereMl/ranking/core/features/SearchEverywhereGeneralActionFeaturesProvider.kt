package com.intellij.searchEverywhereMl.ranking.core.features

import ai.grazie.emb.FloatTextEmbedding
import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.TopHitSEContributor
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.ide.util.gotoByName.getAnActionText
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.ml.embeddings.jvm.indices.EntityId
import com.intellij.platform.ml.embeddings.jvm.wrappers.ActionEmbeddingsStorageWrapper
import com.intellij.platform.ml.embeddings.utils.generateEmbeddingBlocking
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereGeneralActionFeaturesProvider.Fields.IS_ENABLED
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereGeneralActionFeaturesProvider.Fields.IS_HIGH_PRIORITY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereGeneralActionFeaturesProvider.Fields.ITEM_TYPE
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereGeneralActionFeaturesProvider.Fields.TYPE_WEIGHT

internal class SearchEverywhereGeneralActionFeaturesProvider
  : SearchEverywhereElementFeaturesProvider(ActionSearchEverywhereContributor::class.java, TopHitSEContributor::class.java) {
  object Fields {
    internal val IS_ENABLED = EventFields.Boolean("isEnabled")

    internal val ITEM_TYPE = EventFields.Enum<GotoActionModel.MatchedValueType>("type")
    internal val TYPE_WEIGHT = EventFields.Int("typeWeight")
    internal val IS_HIGH_PRIORITY = EventFields.Boolean("isHighPriority")
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> {
    return arrayListOf(
      IS_ENABLED, ITEM_TYPE, TYPE_WEIGHT, IS_HIGH_PRIORITY
    )
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?): List<EventPair<*>> {
    val data = arrayListOf<EventPair<*>>()
    data.addIfTrue(IS_HIGH_PRIORITY, isHighPriority(elementPriority))

    var similarityScore: Double? = null

    // (element is GotoActionModel.MatchedValue) for actions and option provided by 'ActionSearchEverywhereContributor'
    // (element is OptionDescription || element is AnAction) for actions and option provided by 'TopHitSEContributor'
    if (element is GotoActionModel.MatchedValue) {
      data.add(ITEM_TYPE.with(element.type))
      data.add(TYPE_WEIGHT.with(element.valueTypeWeight))

      element.similarityScore?.let { similarityScore = it }
      data.add(IS_SEMANTIC_ONLY.with(element.type == GotoActionModel.MatchedValueType.SEMANTIC))
    }
    else {
      data.add(IS_SEMANTIC_ONLY.with(false))
    }

    val value = if (element is GotoActionModel.MatchedValue) element.value else element
    val valueName = getValueName(value)
    valueName?.let {
      data.addAll(getNameMatchingFeatures(it, searchQuery))
    }
    if (similarityScore != null) {
      data.add(SIMILARITY_SCORE.with(roundDouble(similarityScore!!)))
    }
    else if (ApplicationManager.getApplication().isEAP) { // for now, we can collect the data only from EAP builds
      val action = extractAction(element)
      val actionEmbedding = getActionEmbedding(action, valueName)
      val queryEmbedding = getQueryEmbedding(searchQuery, split = false)
      if (actionEmbedding != null && queryEmbedding != null) {
        data.add(SIMILARITY_SCORE.with(roundDouble(actionEmbedding.cosine(queryEmbedding).toDouble())))
      }
    }

    return data
  }

  private fun getValueName(value: Any): String? {
    return when (value) {
      is String -> value
      is OptionDescription -> GotoActionModel.GotoActionListCellRenderer.calcHit(value)
      is GotoActionModel.ActionWrapper -> value.presentation.text
      is AnAction -> getAnActionText(value)
      else -> null
    }
  }

  private fun extractAction(item: Any): AnAction? {
    if (item is AnAction) return item
    return ((if (item is GotoActionModel.MatchedValue) item.value else item) as? GotoActionModel.ActionWrapper)?.action
  }

  private fun isHighPriority(priority: Int): Boolean = priority >= 11001

  private fun getActionEmbedding(action: AnAction?, actionText: String?): FloatTextEmbedding? {
    var embedding: FloatTextEmbedding? = null
    if (action != null) {
      embedding = ActionManager.getInstance().getId(action)?.let { id ->
        runBlockingMaybeCancellable { ActionEmbeddingsStorageWrapper.getInstance().lookup(EntityId(id)) }
      }
    }
    if (embedding == null && actionText != null) {
      embedding = generateEmbeddingBlocking(actionText)
    }
    return embedding
  }
}