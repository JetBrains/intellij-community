package com.intellij.turboComplete.analysis.usage

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.ml.impl.turboComplete.CompletionKind
import com.intellij.platform.ml.impl.turboComplete.KindVariety
import com.intellij.platform.ml.impl.turboComplete.SuggestionGenerator
import com.intellij.platform.ml.impl.turboComplete.SuggestionGenerator.Companion.suggestionGenerator
import com.intellij.turboComplete.analysis.PipelineListener

@Service
class KindVarietyUsageTracker {
  private val kindStatisticsPerVariety: MutableMap<CompletionKind, CombinedKindUsageTracker> = mutableMapOf()

  private val recentPeriodLength = 30

  fun kindStatistics(kind: CompletionKind): CombinedKindUsageStatistics {
    return kindStatisticsPerVariety
      .getOrDefault(kind, CombinedKindUsageTracker(kind, recentPeriodLength, recentPeriodLength))
      .getSummary()
  }

  fun trackedKinds(variety: KindVariety): List<CompletionKind> {
    return kindStatisticsPerVariety.keys.filter { it.variety == variety }
  }

  fun trackedKinds(parameters: CompletionParameters): List<CompletionKind> {
    return kindStatisticsPerVariety.keys.filter { it.variety.kindsCorrespondToParameters(parameters) }
  }

  private fun kindStatisticsTracker(kind: CompletionKind): CombinedKindUsageTracker {
    return kindStatisticsPerVariety.getOrPut(kind) {
      CombinedKindUsageTracker(kind, recentPeriodLength, recentPeriodLength)
    }
  }

  class UsagePipelineListener : PipelineListener {
    override fun onInitialize(parameters: CompletionParameters) {
      LookupManager.getActiveLookup(parameters.editor)?.addLookupListener(UsageLookupListener())
    }

    override fun onGeneratorCollected(suggestionGenerator: SuggestionGenerator) {
      service<KindVarietyUsageTracker>()
        .kindStatisticsTracker(suggestionGenerator.kind)
        .trackCreated()
    }
  }

  class UsageLookupListener : LookupListener {
    override fun itemSelected(event: LookupEvent) {
      val tracker = service<KindVarietyUsageTracker>()
      val correctSuggestionGenerator = event.item?.suggestionGenerator ?: return

      tracker.kindStatisticsPerVariety.forEach { (kind, kindStatisticsTracker) ->
        kindStatisticsTracker.trackGenerated(correct = kind == correctSuggestionGenerator.kind)
      }
    }
  }
}