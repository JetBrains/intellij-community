package com.intellij.searchEverywhereMl.ranking

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo

object SearchEverywhereRankingDiffCalculator {
  internal fun calculateDiffIfApplicable(seOrderedElementsInfo: List<SearchEverywhereFoundElementInfo>): List<SearchEverywhereFoundElementInfo>? {
    val elementsInfoBeforeDiffWithIndex = seOrderedElementsInfo.filterIndexedIsInstance<SearchEverywhereFoundElementInfoBeforeDiff>()

    // showDiff is on, but ml order is off
    if (elementsInfoBeforeDiffWithIndex.none { it.value.mlWeight != null }) return null

    val seSearchPositions = elementsInfoBeforeDiffWithIndex.map { it.index }
    val elementsInfoBeforeDiff = elementsInfoBeforeDiffWithIndex.map { it.value }

    val seSortedPositions = elementsInfoBeforeDiff.indices
    val mlSortedPositions = elementsInfoBeforeDiff
      .asSequence()
      .withIndex() // original order indexes
      .sortedByDescending {
        SearchEverywhereFoundElementInfoWithMl.getPriority(it.value.element, it.value.priority, it.value.mlWeight)
      } // order by mlPriority
      .withIndex() // add ordering indexes
      .sortedBy { it.value.index } // sort by original indexes
      .map { it.index } // keep only ordering indexes
      .toList()

    val positionToOrderingDiff = seSortedPositions
      .zip(mlSortedPositions).zip(seSearchPositions)
      .associate { (positionsPair, searchPosition) ->
        searchPosition to (positionsPair.first - positionsPair.second)
      }

    return seOrderedElementsInfo.mapIndexed { index, it ->
      if (it is SearchEverywhereFoundElementInfoBeforeDiff) {
        SearchEverywhereFoundElementInfoAfterDiff(
          element = it.element,
          sePriority = it.sePriority,
          contributor = it.contributor,
          mlWeight = it.mlWeight,
          mlFeatures = it.mlFeatures,
          orderingDiff = positionToOrderingDiff.getOrDefault(index, 0),
          seSearchPosition = index
        )
      }
      else {
        it
      }
    }
  }

  internal fun getRankingDiffInfos(mlOrderedElementsInfo: List<SearchEverywhereFoundElementInfo>): List<RankingDiffInfo> {
    return mlOrderedElementsInfo.mapIndexed { mlSearchPosition, elementInfo ->
      if (elementInfo is SearchEverywhereFoundElementInfoAfterDiff) {
        RankingDiffInfo(
          mlPriority = elementInfo.priority,
          sePriority = elementInfo.sePriority,
          mlIndex = mlSearchPosition,
          seIndex = elementInfo.seSearchPosition
        )
      }
      else {
        // invalid values
        RankingDiffInfo(
          mlPriority = -1,
          sePriority = -1,
          mlIndex = -1,
          seIndex = -1
        )
      }
    }
  }

  private inline fun <reified S> List<*>.filterIndexedIsInstance(): List<IndexedValue<S>> {
    return this.withIndex().filter { it.value is S }.map { IndexedValue(it.index, it.value as S) }
  }

  @JsonPropertyOrder("mlPriority", "sePriority", "mlIndex", "seIndex")
  internal data class RankingDiffInfo(val mlPriority: Int, val sePriority: Int, val mlIndex: Int, val seIndex: Int)
}