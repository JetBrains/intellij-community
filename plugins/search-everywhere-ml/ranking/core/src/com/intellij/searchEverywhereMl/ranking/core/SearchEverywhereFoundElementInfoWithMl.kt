package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.internal.statistic.eventLog.events.EventPair
import org.jetbrains.annotations.Contract

internal open class SearchEverywhereFoundElementInfoWithMl(
  element: Any,
  val elementId: Int?,
  val heuristicPriority: Int,
  contributor: SearchEverywhereContributor<*>,
  val mlWeight: Double?,
  mlFeatures: List<EventPair<*>>,
  correction: SearchEverywhereSpellCheckResult = SearchEverywhereSpellCheckResult.NoCorrection,
) : SearchEverywhereFoundElementInfo(element, getPriority(element, heuristicPriority, mlWeight), contributor, correction) {
  private val _mlFeatures: MutableList<EventPair<*>> = mlFeatures.toMutableList()

  val mlFeatures: List<EventPair<*>>
    get() = _mlFeatures.toList()

  fun addMlFeature(feature: EventPair<*>) {
    _mlFeatures.add(feature)
  }

  companion object {
    internal const val MAX_ELEMENT_WEIGHT = 10_000

    @Contract("-> new")
    fun withoutMl(element: Any,
                  priority: Int,
                  contributor: SearchEverywhereContributor<*>,
                  correction: SearchEverywhereSpellCheckResult)
                  = SearchEverywhereFoundElementInfoWithMl(element, null,priority, contributor, null, emptyList(), correction)

    fun from(info: SearchEverywhereFoundElementInfo): SearchEverywhereFoundElementInfoWithMl {
      return when (info) {
        is SearchEverywhereFoundElementInfoWithMl -> info
        is SearchEverywhereFoundElementInfoBeforeDiff -> SearchEverywhereFoundElementInfoWithMl(
          element = info.element,
          elementId = info.elementId,
          heuristicPriority = info.heuristicPriority,
          contributor = info.contributor,
          mlWeight = info.mlWeight,
          mlFeatures = info.mlFeatures,
          correction = info.correction
        )
        else -> {
          withoutMl(info.element, info.priority, info.contributor, info.correction)
        }
      }
    }

    internal fun getPriority(element: Any, priority: Int, mlWeight: Double?): Int {
      if (mlWeight == null) return priority

      val weight = if (element is GotoActionModel.MatchedValue && element.type == GotoActionModel.MatchedValueType.ABBREVIATION) 1.0 else mlWeight
      return (weight * MAX_ELEMENT_WEIGHT).toInt() * 100_000 + priority
    }
  }

  override fun getDescription(): String {
    val sb = StringBuilder()

    val searchProviderId = contributor?.searchProviderId ?: "null"
    val isSpellChecked = correction is SearchEverywhereSpellCheckResult.Correction
    sb.appendLine("Contributor: $searchProviderId")
    sb.appendLine("Corrected by Spell Checker: ${
      if (isSpellChecked) "Yes (correction: ${(correction as SearchEverywhereSpellCheckResult.Correction).correction}, " +
                          "confidence: ${(correction as SearchEverywhereSpellCheckResult.Correction).confidence})"
      else "No"
    }")
    sb.appendLine("Weight: ${priority}")
    sb.appendLine("ML Weight: ${mlWeight}")
    sb.appendLine("ML Features:")
    mlFeatures.forEach { eventPair -> sb.appendLine("${eventPair.field.name}: ${eventPair.data}") }

    return sb.toString()
  }
}