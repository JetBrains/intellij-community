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
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ml.impl.turboComplete.CompletionKind
import com.intellij.platform.ml.impl.turboComplete.KindVariety
import com.intellij.platform.ml.impl.turboComplete.ranking.RankedKind
import com.intellij.turboComplete.features.kind.FeaturesComputer
import com.intellij.codeInsight.completion.ml.ContextFeatures as CompletionContextFeatures

class MLKindSorter(decisionFunction: DecisionFunction, override val kindVariety: KindVariety) : KindRelevanceSorter {
  private val model = LanguageRankingModel(decisionFunction, DecoratingItemsPolicy.DISABLED)

  override fun sort(kinds: List<CompletionKind>,
                    parameters: CompletionParameters): List<RankedKind>? {
    val features = acquireContextFeatures(parameters) ?: return null
    val kindsWeights = kinds.map { it to predict(it, features, parameters) }

    return RankedKind.fromWeights(
      kindsWeights,
      true
    )
  }

  private fun predict(kind: CompletionKind,
                      contextFeatures: ContextFeatures,
                      parameters: CompletionParameters): Double {
    val kindFeatures = FeaturesComputer.getKindFeatures(kind, CompletionLocation(parameters), contextFeatures.completionContextFeatures)
    val allFeatures = contextFeatures.rankingFeatures.withElementFeatures(
      ContextFeaturesStorage(kindFeatures.mapKeys { "ml_completion_kind_${it.key}" }).asMap(),
      emptyMap(),
    )
    return model.score(allFeatures)
  }

  private data class ContextFeatures(
    val completionContextFeatures: CompletionContextFeatures,
    val rankingFeatures: RankingFeatures,
  )

  private fun acquireContextFeatures(parameters: CompletionParameters): ContextFeatures? {
    val lookupStorage: MutableLookupStorage = run {
      val lookup = LookupManager.getActiveLookup(parameters.editor) as? LookupImpl?
                   ?: return null.also { log { "LookupImpl was not detected, performance will be disabled" } }

      val lookupStorage = getCashedLookupStorage(parameters) ?: createLookupStorage(parameters)

      if (!lookupStorage.isContextFactorsInitialized()) {
        ContextFactorCalculator.calculateContextFactors(lookup, parameters, lookupStorage)
      }
      require(lookupStorage.isContextFactorsInitialized())
      lookupStorage
    }

    val rankingFeatures = RankingFeatures(
      lookupStorage.userFactors,
      lookupStorage.contextFactors,
      emptyMap(), emptyMap(), emptySet()
    )

    return ContextFeatures(
      lookupStorage.contextProvidersResult(),
      rankingFeatures
    )
  }

  private fun createLookupStorage(parameters: CompletionParameters): MutableLookupStorage {
    log { "Created lookup storage, as it was not initialized" }
    return MutableLookupStorage(System.currentTimeMillis(), parameters.position.language, null)
  }

  private fun getCashedLookupStorage(parameters: CompletionParameters): MutableLookupStorage? {
    val lookup = LookupManager.getActiveLookup(parameters.editor) as? LookupImpl? ?: return null
    val lookupStorage = MutableLookupStorage.get(lookup) ?: return null
    log { "Acquired cashed context features" }
    return lookupStorage
  }

  private fun log(message: () -> String) {
    if (Registry.`is`("ml.completion.performance.logDebug")) {
      thisLogger().debug(message())
    }
  }
}
