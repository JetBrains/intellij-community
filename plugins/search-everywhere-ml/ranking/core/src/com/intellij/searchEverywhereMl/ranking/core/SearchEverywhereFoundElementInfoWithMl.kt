package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.internal.statistic.eventLog.events.EventPair
import org.jetbrains.annotations.Contract

internal open class SearchEverywhereFoundElementInfoWithMl(
  element: Any,
  val heuristicPriority: Int,
  contributor: SearchEverywhereContributor<*>,
  val mlWeight: Double?,
  mlFeatures: List<EventPair<*>>
) : SearchEverywhereFoundElementInfo(element, getPriority(element, heuristicPriority, mlWeight), contributor) {
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
                  contributor: SearchEverywhereContributor<*>) = SearchEverywhereFoundElementInfoWithMl(element, priority, contributor,
                                                                                                           null, emptyList())

    fun from(info: SearchEverywhereFoundElementInfo): SearchEverywhereFoundElementInfoWithMl {
      return when (info) {
        is SearchEverywhereFoundElementInfoWithMl -> info
        is SearchEverywhereFoundElementInfoBeforeDiff -> SearchEverywhereFoundElementInfoWithMl(
          element = info.element,
          heuristicPriority = info.heuristicPriority,
          contributor = info.contributor,
          mlWeight = info.mlWeight,
          mlFeatures = info.mlFeatures
        )
        else -> {
          withoutMl(info.element, info.priority, info.contributor)
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
    sb.appendLine("Contributor: $searchProviderId")
    sb.appendLine("Weight: ${priority}")
    sb.appendLine("ML Weight: ${mlWeight}")
    sb.appendLine("ML Features:")
    mlFeatures.forEach { eventPair -> sb.appendLine("${eventPair.field.name}: ${eventPair.data}") }

    return sb.toString()
  }
}