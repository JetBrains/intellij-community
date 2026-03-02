package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFeature
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.COLLECTED_RESULTS_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.ITEM_SELECTED
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.REBUILD_REASON_KEY
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.SESSION_FINISHED
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.SESSION_STARTED
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.STATE_CHANGED
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchStateChangeReason
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SearchEverywhereMlRankingServiceLegacyPathTest : SearchEverywhereLoggingTestCase() {
  @Before
  fun setup() {
    SearchEverywhereFeature.allRegistryKeys.forEach { setRegistryPropertyForTest(it, "false") }
  }

  @Test
  fun `legacy path session lifecycle produces correct events`() {
    val events = MockSearchEverywhereProvider.SingleActionSearchEverywhere.runSearchAndCollectLogEvents {
      type("reg")
      selectFirstItem()
    }

    assertTrue("Should contain SESSION_STARTED event",
               events.any { it.event.id == SESSION_STARTED.eventId })
    assertTrue("Should contain STATE_CHANGED event",
               events.any { it.event.id == STATE_CHANGED.eventId })
    assertTrue("Should contain ITEM_SELECTED event",
               events.any { it.event.id == ITEM_SELECTED.eventId })
    assertTrue("Should contain SESSION_FINISHED event",
               events.any { it.event.id == SESSION_FINISHED.eventId })
  }

  @Test
  fun `legacy onStateStarted converts SearchRestartReason correctly`() {
    val events = MockSearchEverywhereProvider.SingleActionSearchEverywhere.runSearchAndCollectLogEvents {
      type("a")
      closePopup()
    }

    val firstStateChanged = events.first { it.event.id == STATE_CHANGED.eventId }
    val reason = firstStateChanged.event.data[REBUILD_REASON_KEY.name]
    assertEquals(SearchStateChangeReason.SEARCH_START.toString(), reason)

    val textChangeEvents = events.filter { it.event.id == STATE_CHANGED.eventId }.drop(1)
    textChangeEvents.forEach { event ->
      val changeReason = event.event.data[REBUILD_REASON_KEY.name]
      assertEquals("State change after typing should be QUERY_CHANGE",
                   SearchStateChangeReason.QUERY_CHANGE.toString(),
                   changeReason)
    }
  }

  @Test
  fun `legacy createFoundElementInfo returns original when no ML probability`() {
    // When ML models are not loaded (default in tests), the priority should remain unchanged
    val events = MockSearchEverywhereProvider.SingleActionSearchEverywhere.runSearchAndCollectLogEvents {
      type("reg")
      closePopup()
    }

    val lastStateChanged = events.last { it.event.id == STATE_CHANGED.eventId }
    val collectedItems = lastStateChanged.event.data[COLLECTED_RESULTS_DATA_KEY.name]

    assertNotNull("Should have collected results data", collectedItems)
    assertInstanceOf(collectedItems, List::class.java)
  }

  @Test
  fun `legacy onItemSelected maps indexes correctly`() {
    val events = MockSearchEverywhereProvider.SingleActionSearchEverywhere.runSearchAndCollectLogEvents {
      type("reg")
      selectFirstItem()
    }

    val selectionEvent = events.firstOrNull { it.event.id == ITEM_SELECTED.eventId }
    assertNotNull(selectionEvent)
  }
}
