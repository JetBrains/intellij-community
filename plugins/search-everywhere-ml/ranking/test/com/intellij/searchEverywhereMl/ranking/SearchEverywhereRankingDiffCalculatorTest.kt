package com.intellij.searchEverywhereMl.ranking

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase


class SearchEverywhereRankingDiffCalculatorTest : BasePlatformTestCase() {


  companion object {
    private const val ML_PRIORITY_WEIGHT_MULTIPLIER = 100_000 * SearchEverywhereFoundElementInfoWithMl.MAX_ELEMENT_WEIGHT


    private fun buildBeforeDiffWithPriorities(
      mlPriorities: List<Int>,
      sePriorities: List<Int> = emptyList()
    ): List<SearchEverywhereFoundElementInfoBeforeDiff> {
      return mlPriorities.mapIndexed { index, mlPriority ->
        val mlWeight = mlPriority / ML_PRIORITY_WEIGHT_MULTIPLIER.toDouble()
        val sePriority = sePriorities.getOrNull(index) ?: 0

        SearchEverywhereFoundElementInfoBeforeDiff(
          element = Any(),
          sePriority = sePriority,
          contributor = MockSearchEverywhereContributor(),
          mlWeight = mlWeight,
          mlFeatures = emptyList()
        )
      }
    }

    private fun mockElementInfoWithMl(element: Any = Any(),
                                      sePriority: Int = 1000,
                                      contributor: MockSearchEverywhereContributor = MockSearchEverywhereContributor(),
                                      mlWeight: Double? = null,
                                      mlFeatures: List<EventPair<*>> = emptyList()): SearchEverywhereFoundElementInfoWithMl {
      return SearchEverywhereFoundElementInfoWithMl(
        element = element,
        sePriority = sePriority,
        contributor = contributor,
        mlWeight = mlWeight,
        mlFeatures = mlFeatures
      )
    }

    private fun mockElementInfoBeforeDiff(element: Any = Any(),
                                          sePriority: Int = 1000,
                                          contributor: MockSearchEverywhereContributor = MockSearchEverywhereContributor(),
                                          mlWeight: Double? = null,
                                          mlFeatures: List<EventPair<*>> = emptyList()): SearchEverywhereFoundElementInfoBeforeDiff {
      return SearchEverywhereFoundElementInfoBeforeDiff(
        element = element,
        sePriority = sePriority,
        contributor = contributor,
        mlWeight = mlWeight,
        mlFeatures = mlFeatures
      )
    }

    private fun getRankingDiffInfos(elementInfos: List<SearchEverywhereFoundElementInfo>): List<SearchEverywhereRankingDiffCalculator.RankingDiffInfo> {
      val beforeDiffs = elementInfos.filterIsInstance<SearchEverywhereFoundElementInfoBeforeDiff>()
      val seOrdered = beforeDiffs.sortedByDescending { it.sePriority }
      val updatedElements = SearchEverywhereRankingDiffCalculator.calculateDiffIfApplicable(seOrdered)
      val mlOrdered = updatedElements?.sortedByDescending { it.priority } ?: emptyList()
      return SearchEverywhereRankingDiffCalculator.getRankingDiffInfos(mlOrdered)
    }
  }

  fun `test calculate diff change only BeforeDiff instances`() {
    val elements = listOf(
      mockElementInfoWithMl(),
      mockElementInfoBeforeDiff(mlWeight = 0.5),
      mockElementInfoBeforeDiff(),
      mockElementInfoBeforeDiff(mlWeight = 0.5),
      mockElementInfoWithMl(),
      mockElementInfoBeforeDiff(),
      mockElementInfoWithMl(),
      mockElementInfoWithMl(),
    )

    val resultClasses = listOf(
      SearchEverywhereFoundElementInfoWithMl::class,
      SearchEverywhereFoundElementInfoAfterDiff::class,
      SearchEverywhereFoundElementInfoAfterDiff::class,
      SearchEverywhereFoundElementInfoAfterDiff::class,
      SearchEverywhereFoundElementInfoWithMl::class,
      SearchEverywhereFoundElementInfoAfterDiff::class,
      SearchEverywhereFoundElementInfoWithMl::class,
      SearchEverywhereFoundElementInfoWithMl::class
    )

    val updatedElements = SearchEverywhereRankingDiffCalculator.calculateDiffIfApplicable(elements)

    TestCase.assertNotNull(updatedElements)

    updatedElements?.zip(resultClasses)?.map { (element, className) ->
      TestCase.assertTrue(className.isInstance(element))
    }

  }

  fun `test calculate diff null ml weights return null`() {
    val elements = listOf(
      mockElementInfoWithMl(),
      mockElementInfoBeforeDiff(),
      mockElementInfoBeforeDiff(),
      mockElementInfoWithMl(),
      mockElementInfoBeforeDiff(),
      mockElementInfoWithMl(),
    )

    TestCase.assertNull(SearchEverywhereRankingDiffCalculator.calculateDiffIfApplicable(elements))
  }

  fun `test calculate diff equal ranking`() {
    val foundElements = buildBeforeDiffWithPriorities(
      mlPriorities = listOf(100_000, 100_000, 90_000),
    )

    val orderingDiffs = getRankingDiffInfos(foundElements)
    UsefulTestCase.assertOrderedEquals(orderingDiffs.map { it.seIndex }, listOf(0, 1, 2))
  }

  fun `test mixed ranking`() {
    val foundElements = buildBeforeDiffWithPriorities(
      mlPriorities = listOf(9_000_000, 7_000_000, 8_000_000, 6_000_000, 3_000_000, 5_000_000, 4_000_000, 1_000_000, 2_000_000),
    )

    val rankingDiffs = getRankingDiffInfos(foundElements)
    val seIndexes = rankingDiffs.map { it.seIndex }
    UsefulTestCase.assertOrderedEquals(seIndexes, listOf(0, 2, 1, 3, 5, 6, 4, 8, 7))
  }

  fun `test ml-inverse ranking`() {
    val foundElements = buildBeforeDiffWithPriorities(
      mlPriorities = listOf(1_000_000, 2_000_000, 3_000_000, 4_000_000, 5_000_000),
    )

    val rankingDiffs = getRankingDiffInfos(foundElements)
    val seIndexes = rankingDiffs.map { it.seIndex }
    UsefulTestCase.assertOrderedEquals(seIndexes, listOf(4, 3, 2, 1, 0))
  }

  fun `test se-inverse ranking`() {
    val foundElements = buildBeforeDiffWithPriorities(
      mlPriorities = listOf(5_000_000, 4_000_000, 3_000_000, 2_000_000, 1_000_000),
      sePriorities = listOf(100, 200, 300, 400, 500)
    )
    val rankingDiffs = getRankingDiffInfos(foundElements)
    val seIndexes = rankingDiffs.map { it.seIndex }
    UsefulTestCase.assertOrderedEquals(seIndexes, listOf(4, 3, 2, 1, 0))
  }

  fun `test ml-se-inverse ranking`() {
    val foundElements = buildBeforeDiffWithPriorities(
      mlPriorities = listOf(1_000_000, 2_000_000, 3_000_000, 4_000_000, 5_000_000),
      sePriorities = listOf(100, 200, 300, 400, 500)
    )
    val rankingDiffs = getRankingDiffInfos(foundElements)
    val seIndexes = rankingDiffs.map { it.seIndex }
    UsefulTestCase.assertOrderedEquals(seIndexes, listOf(0, 1, 2, 3, 4))
  }
}