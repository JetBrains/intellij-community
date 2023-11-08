package com.intellij.turboComplete.ranking

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.features.ContextFeaturesStorage
import com.intellij.completion.ml.sorting.ContextFactorCalculator
import com.intellij.completion.ml.sorting.LanguageRankingModel
import com.intellij.completion.ml.sorting.RankingFeatures
import com.intellij.completion.ml.storage.MutableLookupStorage
import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.completion.DecoratingItemsPolicy
import com.intellij.platform.ml.impl.turboComplete.CompletionKind
import com.intellij.platform.ml.impl.turboComplete.KindVariety
import com.intellij.platform.ml.impl.turboComplete.ranking.RankedKind
import com.intellij.turboComplete.features.kind.FeaturesComputer

class MLKindSorter(private val decisionFunction: DecisionFunction, override val kindVariety: KindVariety) : KindRelevanceSorter {

  private val model = LanguageRankingModel(decisionFunction, DecoratingItemsPolicy.DISABLED)

  override fun sort(kinds: List<CompletionKind>,
                    parameters: CompletionParameters): List<RankedKind>? {

    val lookup = LookupManager.getActiveLookup(parameters.editor) as? LookupImpl? ?: return null

    val lookupStorage = MutableLookupStorage.get(lookup)

    requireNotNull(lookupStorage)

    if (!lookupStorage.isContextFactorsInitialized()) {
      ContextFactorCalculator.calculateContextFactors(lookup, parameters, lookupStorage)
    }
    require(lookupStorage.isContextFactorsInitialized())

    val contextFeatures = RankingFeatures(
      lookupStorage.userFactors,
      lookupStorage.contextFactors,
      emptyMap(), emptyMap(), emptySet()
    )

    val kindsWeights = kinds.map { it to predict(it, lookupStorage, parameters, contextFeatures) }

    return RankedKind.fromWeights(
      kindsWeights,
      true
    )
  }

  private fun predict(kind: CompletionKind,
                      lookupStorage: MutableLookupStorage,
                      parameters: CompletionParameters,
                      contextFeatures: RankingFeatures): Double {
    val kindFeatures = FeaturesComputer.getKindFeatures(kind, CompletionLocation(parameters), lookupStorage.contextProvidersResult())
    val allFeatures = contextFeatures.withElementFeatures(
      ContextFeaturesStorage(kindFeatures.mapKeys { "ml_completion_kind_${it.key}" }).asMap(),
      emptyMap(),
    )
    return model.score(allFeatures)
  }
}
