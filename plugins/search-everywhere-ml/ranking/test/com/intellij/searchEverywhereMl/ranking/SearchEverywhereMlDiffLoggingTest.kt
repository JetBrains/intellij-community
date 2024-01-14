package com.intellij.searchEverywhereMl.ranking

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereMlService
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.searchEverywhereMl.ranking.SearchEverywhereMLStatisticsCollector.COLLECTED_RESULTS_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.SearchEverywhereMLStatisticsCollector.CONTRIBUTOR_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.SearchEverywhereMLStatisticsCollector.FEATURES_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.SearchEverywhereMLStatisticsCollector.ID_KEY
import com.intellij.searchEverywhereMl.ranking.SearchEverywhereMLStatisticsCollector.ML_WEIGHT_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereActionFeaturesProvider.Fields.IS_ACTION_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereActionFeaturesProvider.Fields.TEXT_LENGTH_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereContributorFeaturesProvider.Companion.CONTRIBUTOR_INFO_ID
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereContributorFeaturesProvider.Companion.CONTRIBUTOR_WEIGHT
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereGeneralActionFeaturesProvider.Fields.IS_ENABLED
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereGeneralActionFeaturesProvider.Fields.IS_HIGH_PRIORITY
import com.intellij.searchEverywhereMl.ranking.features.assert
import com.intellij.searchEverywhereMl.ranking.features.get
import com.intellij.searchEverywhereMl.ranking.id.ElementKeyForIdProvider
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class SearchEverywhereMlDiffLoggingTest : SearchEverywhereLoggingTestCase() {
  private val mockRankingService by lazy { MockSearchEverywhereMlService() }

  @Test
  fun `checks with single element that only changed data is recorded`() {
    SearchEverywhereMlService.EP_NAME.point.maskForSingleTest(listOf(mockRankingService))
    ElementKeyForIdProvider.EP_NAME.point.maskForSingleTest(listOf(MockElementKeyForIdProvider()))

    val events = runSearchEverywhereAndCollectLogEvents {
      type("regist")
    }

    val iterator = events.iterator()
    iterator.next()

    checkItemFirstReport(iterator.next())
    checkItemSecondReport(iterator.next())
    checkItemThirdReport(iterator.next())
    checkItemFourthReport(iterator.next())
  }

  private fun checkItemFirstReport(event: LogEvent) {
    // Check that all information is reported - ml weight, features, id, and contributor info
    event.event.data[COLLECTED_RESULTS_DATA_KEY].first()
      .assert(ML_WEIGHT_KEY, 0.5)
      .also {
        it[FEATURES_DATA_KEY]
          .assert(IS_ENABLED, false)
          .assert(TEXT_LENGTH_KEY, 8)
          .assert(IS_ACTION_DATA_KEY, true)
      }
      .assert(ID_KEY, 1)
      .also {
        it[CONTRIBUTOR_DATA_KEY]
          .assert(CONTRIBUTOR_INFO_ID, ActionSearchEverywhereContributor::class.java.simpleName)
          .assert(CONTRIBUTOR_WEIGHT, 0)
      }
  }

  private fun checkItemSecondReport(event: LogEvent) {
    // The second time the item appears in the list, its score and features are exactly the same, thus only ID should be reported
    val collectedItemData = event.event.data[COLLECTED_RESULTS_DATA_KEY].first()

    Assert.assertEquals(1, collectedItemData.keys.size)
    Assert.assertEquals(ID_KEY.name, collectedItemData.keys.first())
  }

  private fun checkItemThirdReport(event: LogEvent) {
    // The third time it appears, the ML weight changes
    val collectedItemData = event.event.data[COLLECTED_RESULTS_DATA_KEY].first()

    collectedItemData.assert(ML_WEIGHT_KEY, 0.75)
      .also { it[FEATURES_DATA_KEY].isEmpty() }
  }

  private fun checkItemFourthReport(event: LogEvent) {
    // The fourth time, IS_ENABLED feature changes - no other features should be reported
    val collectedItemData = event.event.data[COLLECTED_RESULTS_DATA_KEY].first()

    Assert.assertEquals(listOf(FEATURES_DATA_KEY.name, ID_KEY.name), collectedItemData.keys.toList())

    collectedItemData[FEATURES_DATA_KEY]
      .also { Assert.assertEquals(1, it.keys.size) }
      .assert(IS_ENABLED, true)
  }

  class MockSearchEverywhereMlService(private val delegate: SearchEverywhereMlRankingService = SearchEverywhereMlRankingService())
    : SearchEverywhereMlService by delegate {

    private var instanceCounter = 0
    private lateinit var baseFoundElementInfo: SearchEverywhereFoundElementInfoWithMl

    override fun createFoundElementInfo(contributor: SearchEverywhereContributor<*>,
                                        element: Any,
                                        priority: Int): SearchEverywhereFoundElementInfo {
      return nextFoundElementInfo(element, contributor)
    }

    private fun nextFoundElementInfo(element: Any, contributor: SearchEverywhereContributor<*>): SearchEverywhereFoundElementInfo {
      return when (instanceCounter) {
        0 -> SearchEverywhereFoundElementInfoWithMl(element, 1000, contributor, 0.5, listOf(
          IS_ENABLED.with(false),
          IS_ACTION_DATA_KEY.with(true),
          TEXT_LENGTH_KEY.with(8),
        ))
        1 -> baseFoundElementInfo
        2 -> baseFoundElementInfo.copy(mlWeight = 0.75)
        3 -> baseFoundElementInfo.copy(mlFeatures = listOf(
          IS_ENABLED.with(true),
          IS_ACTION_DATA_KEY.with(true),
          TEXT_LENGTH_KEY.with(8),
        ))
        4 -> baseFoundElementInfo.copy(mlWeight = 0.9, mlFeatures = listOf(
          IS_ENABLED.with(false),
          IS_ACTION_DATA_KEY.with(true),
          TEXT_LENGTH_KEY.with(8),
          IS_HIGH_PRIORITY.with(true),
        ))
        else -> baseFoundElementInfo
      }.also {
        baseFoundElementInfo = it
        instanceCounter++
      }
    }

    private fun SearchEverywhereFoundElementInfoWithMl.copy(element: Any = this.element,
                                                            sePriority: Int = this.sePriority,
                                                            contributor: SearchEverywhereContributor<*> = this.contributor,
                                                            mlWeight: Double? = this.mlWeight,
                                                            mlFeatures: List<EventPair<*>> = this.mlFeatures) =
      SearchEverywhereFoundElementInfoWithMl(element, sePriority, contributor, mlWeight, mlFeatures)
  }

  class MockElementKeyForIdProvider : ElementKeyForIdProvider {
    override fun getKey(element: Any): Any {
      return Unit
    }
  }
}