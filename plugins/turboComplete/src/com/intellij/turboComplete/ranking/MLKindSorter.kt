package com.intellij.turboComplete.ranking

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.features.ContextFeaturesStorage
import com.intellij.completion.ml.sorting.LanguageRankingModel
import com.intellij.completion.ml.sorting.RankingFeatures
import com.intellij.completion.ml.storage.MutableLookupStorage
import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.completion.DecoratingItemsPolicy
import com.intellij.platform.ml.impl.turboComplete.CompletionKind
import com.intellij.platform.ml.impl.turboComplete.KindVariety
import com.intellij.platform.ml.impl.turboComplete.ranking.RankedKind
import com.intellij.turboComplete.features.kind.FeaturesComputer

class MLKindSorter(decisionFunction: DecisionFunction, override val kindVariety: KindVariety) : KindRelevanceSorter {

  private val model = LanguageRankingModel(decisionFunction, DecoratingItemsPolicy.DISABLED)

  override fun sort(kinds: List<CompletionKind>,
                    parameters: CompletionParameters): List<RankedKind> {

    val lookup = LookupManager.getActiveLookup(parameters.editor) as? LookupImpl

    requireNotNull(lookup) { "Unable to perform Completion Kind ordering when Lookup is not LookupImpl" }

    val lookupStorage = MutableLookupStorage.get(lookup)

    requireNotNull(lookupStorage)
    require(lookupStorage.isContextFactorsInitialized())

    val kindsWeights = kinds.map { it to predict(it, lookupStorage, parameters) }

    return RankedKind.fromWeights(
      kindsWeights,
      true
    )
  }

  private fun predict(kind: CompletionKind, lookupStorage: MutableLookupStorage, parameters: CompletionParameters): Double {
    val rankingFeatures = RankingFeatures(
      lookupStorage.userFactors,
      lookupStorage.contextFactors,
      emptyMap(), emptyMap(), emptySet()
    )

    val kindFeatures = FeaturesComputer.getKindFeatures(kind, CompletionLocation(parameters), lookupStorage.contextProvidersResult())

    return model.score(rankingFeatures.withElementFeatures(
      ContextFeaturesStorage(kindFeatures).asMap(),
      emptyMap(),
    ))
  }
}
