package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereMlService
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.util.use
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.COLLECTED_RESULTS_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.CONTRIBUTOR_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.FEATURES_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.ID_KEY
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.ML_WEIGHT_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.IS_ACTION_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.TEXT_LENGTH_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereContributorFeaturesProvider.Companion.CONTRIBUTOR_INFO_ID
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereContributorFeaturesProvider.Companion.CONTRIBUTOR_WEIGHT
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereElementFeaturesProvider.Companion.BUFFERED_TIMESTAMP
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereGeneralActionFeaturesProvider.Fields.IS_ENABLED
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereGeneralActionFeaturesProvider.Fields.IS_HIGH_PRIORITY
import com.intellij.searchEverywhereMl.ranking.core.features.assert
import com.intellij.searchEverywhereMl.ranking.core.features.get
import com.intellij.searchEverywhereMl.ranking.ext.SearchEverywhereElementKeyProvider
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import org.junit.Assert
import org.junit.Test
import org.junit.jupiter.api.Assertions
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class SearchEverywhereMlDiffLoggingTest : SearchEverywhereLoggingTestCase() {
  @Test
  fun `checks with single element that only changed data is recorded`() {
    val events = SearchEverywhereMlService.EP_NAME.maskedWith(listOf(MockSearchEverywhereMlService())).use {
      SearchEverywhereElementKeyProvider.EP_NAME.maskedWith(listOf(MockElementKeyForIdProvider())).use {

        MockSearchEverywhereProvider.SingleActionSearchEverywhere.runSearchAndCollectLogEvents {
          type("regist")
        }

      }
    }

    val iterator = events.iterator()
    iterator.next()  // SE opened event

    checkItemFirstReport(iterator.next())
    checkItemSecondReport(iterator.next())
    checkItemThirdReport(iterator.next())
    checkItemFourthReport(iterator.next())
  }

  @Test
  fun `check that no diff logging applies between two search everywhere runs`() {
    // This test addresses IDEA-345677
    val firstSERunEvents = SearchEverywhereMlService.EP_NAME.maskedWith(listOf(MockSearchEverywhereMlService())).use {
      SearchEverywhereElementKeyProvider.EP_NAME.maskedWith(listOf(MockElementKeyForIdProvider())).use {
        MockSearchEverywhereProvider.SingleActionSearchEverywhere.runSearchAndCollectLogEvents { type("reg") }
      }
    }

    val secondSERunEvents = SearchEverywhereMlService.EP_NAME.maskedWith(listOf(MockSearchEverywhereMlService())).use {
      SearchEverywhereElementKeyProvider.EP_NAME.maskedWith(listOf(MockElementKeyForIdProvider())).use {
        MockSearchEverywhereProvider.SingleActionSearchEverywhere.runSearchAndCollectLogEvents { type("reg") }
      }
    }

    // We expect both first reports to have the same features
    Assertions.assertEquals(firstSERunEvents.firstEventWithCollectedItems().event.data[COLLECTED_RESULTS_DATA_KEY].first().keys,
                            secondSERunEvents.firstEventWithCollectedItems().event.data[COLLECTED_RESULTS_DATA_KEY].first().keys)
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
    // The second time the item appears, the score and features (except for bufferedTimestamp) remain the same,
    // thus only ID and BUFFERED_TIMESTAMP should be reported.

    val collectedItemData = event.event.data[COLLECTED_RESULTS_DATA_KEY].first()

    Assert.assertEquals(setOf(ID_KEY.name, FEATURES_DATA_KEY.name), collectedItemData.keys.toSet())

    collectedItemData[FEATURES_DATA_KEY]
      .also {
        Assert.assertEquals(1, it.keys.size)
        Assert.assertTrue(it.keys.contains(BUFFERED_TIMESTAMP.name))
      }
  }

  private fun checkItemThirdReport(event: LogEvent) {
    // The third time it appears, the ML weight changes
    val collectedItemData = event.event.data[COLLECTED_RESULTS_DATA_KEY].first()

    collectedItemData.assert(ML_WEIGHT_KEY, 0.75)
      .also { it[FEATURES_DATA_KEY].isEmpty() }
  }

  private fun checkItemFourthReport(event: LogEvent) {
    // The fourth time, IS_ENABLED and BUFFERED_TIMESTAMPfeature change - no other features should be reported
    val collectedItemData = event.event.data[COLLECTED_RESULTS_DATA_KEY].first()

    Assert.assertEquals(listOf(FEATURES_DATA_KEY.name, ID_KEY.name), collectedItemData.keys.toList())

    collectedItemData[FEATURES_DATA_KEY]
      .also {
        Assert.assertEquals(2, it.keys.size)
        Assert.assertTrue(it.keys.containsAll(listOf(IS_ENABLED.name, BUFFERED_TIMESTAMP.name)))
        Assert.assertTrue(it[IS_ENABLED.name] == true)
        Assert.assertNotNull(it[BUFFERED_TIMESTAMP.name])
      }
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
                                                            sePriority: Int = this.heuristicPriority,
                                                            contributor: SearchEverywhereContributor<*> = this.contributor,
                                                            mlWeight: Double? = this.mlWeight,
                                                            mlFeatures: List<EventPair<*>> = this.mlFeatures) =
      SearchEverywhereFoundElementInfoWithMl(element, sePriority, contributor, mlWeight, mlFeatures)
  }

  class MockElementKeyForIdProvider : SearchEverywhereElementKeyProvider {
    override fun getKeyOrNull(element: Any): Any {
      return Unit
    }
  }
}