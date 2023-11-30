package com.intellij.searchEverywhereMl.ranking.features

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.TopHitSEContributor
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.ide.util.gotoByName.getAnActionText
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereGeneralActionFeaturesProvider.Fields.ACTION_SIMILARITY_SCORE
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereGeneralActionFeaturesProvider.Fields.IS_ACTION_PURE_SEMANTIC
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereGeneralActionFeaturesProvider.Fields.IS_ENABLED
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereGeneralActionFeaturesProvider.Fields.IS_HIGH_PRIORITY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereGeneralActionFeaturesProvider.Fields.ITEM_TYPE
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereGeneralActionFeaturesProvider.Fields.TYPE_WEIGHT

internal class SearchEverywhereGeneralActionFeaturesProvider
  : SearchEverywhereElementFeaturesProvider(ActionSearchEverywhereContributor::class.java, TopHitSEContributor::class.java) {
  object Fields {
    internal val IS_ENABLED = EventFields.Boolean("isEnabled")

    internal val ITEM_TYPE = EventFields.Enum<GotoActionModel.MatchedValueType>("type")
    internal val TYPE_WEIGHT = EventFields.Int("typeWeight")
    internal val ACTION_SIMILARITY_SCORE = EventFields.Double("actionSimilarityScore")
    internal val IS_ACTION_PURE_SEMANTIC = EventFields.Boolean("isActionPureSemantic")
    internal val IS_HIGH_PRIORITY = EventFields.Boolean("isHighPriority")
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> {
    return arrayListOf(
      IS_ENABLED, ITEM_TYPE, TYPE_WEIGHT, IS_HIGH_PRIORITY, ACTION_SIMILARITY_SCORE, IS_ACTION_PURE_SEMANTIC
    )
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?): List<EventPair<*>> {
    val data = arrayListOf<EventPair<*>>()
    data.addIfTrue(IS_HIGH_PRIORITY, isHighPriority(elementPriority))

    // (element is GotoActionModel.MatchedValue) for actions and option provided by 'ActionSearchEverywhereContributor'
    // (element is OptionDescription || element is AnAction) for actions and option provided by 'TopHitSEContributor'
    if (element is GotoActionModel.MatchedValue) {
      data.add(ITEM_TYPE.with(element.type))
      data.add(TYPE_WEIGHT.with(element.valueTypeWeight))

      element.similarityScore?.let {
        data.add(ACTION_SIMILARITY_SCORE.with(it))
      }
      data.add(IS_ACTION_PURE_SEMANTIC.with(element.type == GotoActionModel.MatchedValueType.SEMANTIC))
    }

    val value = if (element is GotoActionModel.MatchedValue) element.value else element
    val valueName = getValueName(value)
    valueName?.let {
      data.addAll(getNameMatchingFeatures(it, searchQuery))
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

  private fun isHighPriority(priority: Int): Boolean = priority >= 11001
}