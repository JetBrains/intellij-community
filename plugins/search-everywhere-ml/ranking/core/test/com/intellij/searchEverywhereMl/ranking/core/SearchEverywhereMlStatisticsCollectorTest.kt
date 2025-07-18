@file:OptIn(IntellijInternalApi::class)

package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.actions.searcheverywhere.SearchRestartReason
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.searchEverywhereMl.features.SearchEverywhereStateFeaturesProvider
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.COLLECTED_RESULTS_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.CONTRIBUTOR_FEATURES_LIST
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.ELEMENT_CONTRIBUTOR
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.ITEM_SELECTED
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.REBUILD_REASON_KEY
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.SEARCH_RESTARTED
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.SESSION_DURATION
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.SESSION_FINISHED
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.SESSION_ID
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.SESSION_STARTED
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SearchEverywhereMlStatisticsCollectorTest : SearchEverywhereLoggingTestCase() {
  val actionSelectionEvents by lazy {
    MockSearchEverywhereProvider.SingleActionSearchEverywhere.runSearchAndCollectLogEvents {
      type("reg")
      selectFirstItem()
    }
  }

  val immediatelyCancelledSessionEvents by lazy {
    MockSearchEverywhereProvider.SingleActionSearchEverywhere.runSearchAndCollectLogEvents {
      closePopup()
    }
  }

  @Test
  fun `SE open and instant close results in one session started and one session finished event`() {
    assertEquals(3, immediatelyCancelledSessionEvents.size)
    assertEquals(SESSION_STARTED.eventId, immediatelyCancelledSessionEvents.first().event.id)
    assertEquals(SEARCH_RESTARTED.eventId, immediatelyCancelledSessionEvents[1].event.id)
    assertEquals(SESSION_FINISHED.eventId, immediatelyCancelledSessionEvents.last().event.id)
  }

  @Test
  fun `first search restarted event has search started restart reason`() {
    val searchRestartedEvent = immediatelyCancelledSessionEvents.first { it.event.id == SEARCH_RESTARTED.eventId }

    assertTrue("${SEARCH_RESTARTED.eventId} event should contain ${REBUILD_REASON_KEY.name} in its data",
               REBUILD_REASON_KEY.name in searchRestartedEvent.event.data)

    val reportedRestartRestored = searchRestartedEvent.event.data[REBUILD_REASON_KEY.name]

    assertTrue("First ${SEARCH_RESTARTED.eventId} event's ${REBUILD_REASON_KEY.name} should be ${SearchRestartReason.SEARCH_STARTED}",
               reportedRestartRestored == SearchRestartReason.SEARCH_STARTED.toString())
  }

  @Test
  fun `all events are reported`() {
    assertEquals("Expected 1 SESSION_STARTED event",
                 1, actionSelectionEvents.count { it.event.id == SESSION_STARTED.eventId })

    assertEquals("Expected 3 SEARCH_RESTARTED events",
                 4, actionSelectionEvents.count { it.event.id == SEARCH_RESTARTED.eventId })

    assertEquals("Expected 1 ITEM_SELECTED event",
                 1, actionSelectionEvents.count { it.event.id == ITEM_SELECTED.eventId })

    assertEquals("Expected 1 SESSION_FINISHED event",
                 1, actionSelectionEvents.count { it.event.id == SESSION_FINISHED.eventId })
  }

  @Test
  fun `session duration is reported in session finished event`() {
    val sessionFinishedEvent = actionSelectionEvents.first { it.event.id == SESSION_FINISHED.eventId }

    assertTrue("SESSION_FINISHED event that closes a popup should have Search Everywhere session duration",
               SESSION_DURATION.name in sessionFinishedEvent.event.data)
  }

  @Test
  fun `item has a contributor info`() {
    val lastEventData = actionSelectionEvents.last { it.event.id == SEARCH_RESTARTED.eventId }.event.data
    val collectedItems = assertInstanceOf(lastEventData[COLLECTED_RESULTS_DATA_KEY.name], List::class.java)
    val firstItem = assertInstanceOf(collectedItems.first(), Map::class.java)
    assertTrue(ELEMENT_CONTRIBUTOR.name in firstItem)
    assertInstanceOf(firstItem[ELEMENT_CONTRIBUTOR.name], String::class.java)
  }

  @Test
  fun `contributor features are in a separate list`() {
    val lastEventData = actionSelectionEvents.last { it.event.id == SEARCH_RESTARTED.eventId }.event.data

    assertTrue(CONTRIBUTOR_FEATURES_LIST.name in lastEventData)

    val contributorFeatures = assertInstanceOf(lastEventData[CONTRIBUTOR_FEATURES_LIST.name], List::class.java)
    assertInstanceOf(contributorFeatures.first(), Map::class.java)
  }

  @Test
  fun `every event has search session id`() {
    val eventsMissingSessionId = actionSelectionEvents.filterNot { event -> SESSION_ID.name in event.event.data }

    if (eventsMissingSessionId.isNotEmpty()) {
      fail("The following events did not report ${SESSION_ID.name}: ${eventsMissingSessionId.joinToString(", ") { it.event.id }}")
    }
  }

  @Test
  fun `search with no results is properly reported`() {
    val provider = MockSearchEverywhereProvider { project ->
      SearchEverywhereUI(project, listOf(
        MockSearchEverywhereContributor(ActionSearchEverywhereContributor::class.java.simpleName, true) { pattern, _, _ ->
        }
      ))
    }

    val events = provider.runSearchAndCollectLogEvents {
      type("nonexistent")
      closePopup()
    }

    val searchRestartedEvents = events.filter { it.event.id == SEARCH_RESTARTED.eventId }
    assertNotEmpty(searchRestartedEvents)

    val lastSearchRestartedEvent = searchRestartedEvents.last()
    val collectedItems = assertInstanceOf(lastSearchRestartedEvent.event.data[COLLECTED_RESULTS_DATA_KEY.name], List::class.java)
    assertTrue("Expected empty list of collected items for non-existent search", collectedItems.isEmpty())

    assertEquals("Total number of items should be 0 for no results",
                 0, collectedItems.size)
  }

  @Test
  fun `session started event contains required fields`() {
    val sessionStartedEvent = immediatelyCancelledSessionEvents.first { it.event.id == SESSION_STARTED.eventId }
    val data = sessionStartedEvent.event.data

    val requiredEvents = SESSION_STARTED.getFields() - SearchEverywhereStateFeaturesProvider.getFields().toSet()

    requiredEvents.forEach { field ->
      assertTrue("${SESSION_STARTED.eventId} event should contain ${field.name}", field.name in data)
    }
  }

  @Test
  fun `search restarted events contain required fields`() {
    actionSelectionEvents.filter { it.event.id == SEARCH_RESTARTED.eventId }
      .forEach { event ->
        val data = event.event.data

        SEARCH_RESTARTED.getFields().forEach { field ->
          assertTrue("${SEARCH_RESTARTED.eventId} event should contain ${field.name}", field.name in data)
        }
      }
  }

  @Test
  fun `item selection event contains required fields`() {
    val selectionEvent = actionSelectionEvents.first { it.event.id == ITEM_SELECTED.eventId }
    val data = selectionEvent.event.data

    ITEM_SELECTED.getFields().forEach { field ->
      assertTrue("${ITEM_SELECTED.eventId} event should contain ${field.name}", field.name in data)
    }
  }

  @Test
  fun `search index is properly incremented in search restarted events`() {
    val events = MockSearchEverywhereProvider.SingleActionSearchEverywhere.runSearchAndCollectLogEvents {
      type("a")
      type("b")
      type("c")
      closePopup()
    }

    val searchRestartedEvents = events.filter { it.event.id == SEARCH_RESTARTED.eventId }
    assertNotEmpty(searchRestartedEvents)

    // Check that search index is incremented for each search restart
    var previousIndex = -1
    for (event in searchRestartedEvents) {
      val currentIndex = event.event.data["searchIndex"] as Int
      assertTrue("Search index should be incremented", currentIndex > previousIndex)
      previousIndex = currentIndex
    }
  }
}