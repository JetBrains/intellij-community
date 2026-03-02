@file:OptIn(IntellijInternalApi::class)

package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFeature
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.ListEventField
import com.intellij.internal.statistic.eventLog.events.ObjectEventField
import com.intellij.internal.statistic.eventLog.events.ObjectListEventField
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.COLLECTED_RESULTS_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.CONTRIBUTOR_FEATURES_LIST
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.ELEMENT_CONTRIBUTOR
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.GROUP
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.ITEM_SELECTED
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.REBUILD_REASON_KEY
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.SEARCH_INDEX_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.SESSION_DURATION
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.SESSION_FINISHED
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.SESSION_ID
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.SESSION_STARTED
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.SELECTED_RESULT_ID
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.STATE_CHANGED
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchStateChangeReason
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereStateFeaturesProvider
import org.junit.Before
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

  @Before
  fun setup() {
    SearchEverywhereFeature.allRegistryKeys.forEach { setRegistryPropertyForTest(it, "false") }
  }

  @Test
  fun `SE open and instant close results in one session started and one session finished event`() {
    assertEquals(3, immediatelyCancelledSessionEvents.size)
    assertEquals(SESSION_STARTED.eventId, immediatelyCancelledSessionEvents.first().event.id)
    assertEquals(STATE_CHANGED.eventId, immediatelyCancelledSessionEvents[1].event.id)
    assertEquals(SESSION_FINISHED.eventId, immediatelyCancelledSessionEvents.last().event.id)
  }

  @Test
  fun `first search restarted event has search started restart reason`() {
    val searchRestartedEvent = immediatelyCancelledSessionEvents.first { it.event.id == STATE_CHANGED.eventId }

    assertTrue("${STATE_CHANGED.eventId} event should contain ${REBUILD_REASON_KEY.name} in its data",
               REBUILD_REASON_KEY.name in searchRestartedEvent.event.data)

    val reportedRestartRestored = searchRestartedEvent.event.data[REBUILD_REASON_KEY.name]

    assertTrue("First ${STATE_CHANGED.eventId} event's ${REBUILD_REASON_KEY.name} should be ${SearchStateChangeReason.SEARCH_START} " +
               "Got $reportedRestartRestored",
               reportedRestartRestored == SearchStateChangeReason.SEARCH_START.toString())
  }

  @Test
  fun `all events are reported`() {
    assertEquals("Expected 1 SESSION_STARTED event",
                 1, actionSelectionEvents.count { it.event.id == SESSION_STARTED.eventId })

    assertEquals("Expected 3 STATE_CHANGED events",
                 4, actionSelectionEvents.count { it.event.id == STATE_CHANGED.eventId })

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
    val lastEventData = actionSelectionEvents.last { it.event.id == STATE_CHANGED.eventId }.event.data
    val collectedItems = assertInstanceOf(lastEventData[COLLECTED_RESULTS_DATA_KEY.name], List::class.java)
    val firstItem = assertInstanceOf(collectedItems.first(), Map::class.java)
    assertTrue(ELEMENT_CONTRIBUTOR.name in firstItem)
    assertInstanceOf(firstItem[ELEMENT_CONTRIBUTOR.name], String::class.java)
  }

  @Test
  fun `contributor features are in a separate list`() {
    val lastEventData = actionSelectionEvents.last { it.event.id == STATE_CHANGED.eventId }.event.data

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
        MockSearchEverywhereContributor(ActionSearchEverywhereContributor::class.java.simpleName, true) { _, _, _ ->
        }
      ))
    }

    val events = provider.runSearchAndCollectLogEvents {
      type("nonexistent")
      closePopup()
    }

    val searchRestartedEvents = events.filter { it.event.id == STATE_CHANGED.eventId }
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

    val requiredEvents = SESSION_STARTED.getFields() - SearchEverywhereStateFeaturesProvider.allFields.toSet()

    requiredEvents.forEach { field ->
      assertTrue("${SESSION_STARTED.eventId} event should contain ${field.name}", field.name in data)
    }
  }

  @Test
  fun `search restarted events contain required fields`() {
    actionSelectionEvents.filter { it.event.id == STATE_CHANGED.eventId }
      .forEach { event ->
        val data = event.event.data

        STATE_CHANGED.getFields().forEach { field ->
          assertTrue("${STATE_CHANGED.eventId} event should contain ${field.name}", field.name in data)
        }
      }
  }

  @Test
  fun `item selection event contains required fields`() {
    val selectionEvent = actionSelectionEvents.first { it.event.id == ITEM_SELECTED.eventId }
    val data = selectionEvent.event.data

    val requiredFields = ITEM_SELECTED.getFields() - SELECTED_RESULT_ID
    requiredFields.forEach { field ->
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

    val searchRestartedEvents = events.filter { it.event.id == STATE_CHANGED.eventId }
    assertNotEmpty(searchRestartedEvents)

    // Check that search index is incremented for each search restart
    var previousIndex = -1
    for (event in searchRestartedEvents) {
      val currentIndex = event.event.data[SEARCH_INDEX_DATA_KEY.name] as Int
      assertTrue("Search index should be incremented", currentIndex > previousIndex)
      previousIndex = currentIndex
    }
  }

  @Test
  fun `no duplicate field names under different object-fields`() {
    // This test is here to ensure that no field names are duplicated across different object allFields
    // Otherwise, this will lead to processing issues on the pipeline side
    val errors = mutableListOf<String>()

    GROUP.events.forEach { event ->
      event.getFields()
        .getFieldsRecursively()
        .groupingBy { it.name }
        .eachCount()
        .filter { it.value > 1 }
        .map { it.key }
        .forEach { duplicatedFieldName ->
          errors.add("Event ${event.eventId} has duplicate field name $duplicatedFieldName")
        }
    }

    if (errors.isNotEmpty()) {
      fail(errors.joinToString("\n"))
    }
  }

  @Test
  fun `all EventField names are snake_case`() {
    val snakeCase = Regex("^[a-z0-9]+(?:_[a-z0-9]+)*$")
    val errors = mutableListOf<String>()

    GROUP.events.forEach { event ->
      event.getFields()
        .getFieldsRecursively()
        .forEach { field ->
          if (!snakeCase.matches(field.name)) {
            errors.add("Event ${event.eventId} has non-snake_case field name '${field.name}'")
          }
        }
    }

    if (errors.isNotEmpty()) {
      fail(errors.joinToString("\n"))
    }
  }

  @Test
  fun `all event ids are lowercase dot separated words`() {
    val dotSeparatedLower = Regex("^[a-z0-9]+(?:\\.[a-z0-9]+)*$")
    val errors = mutableListOf<String>()

    GROUP.events.forEach { event ->
      if (!dotSeparatedLower.matches(event.eventId)) {
        errors.add("Event id '${event.eventId}' is not lowercase dot-separated words")
      }
    }

    if (errors.isNotEmpty()) {
      fail(errors.joinToString("\n"))
    }
  }

  private fun List<EventField<*>>.getFieldsRecursively(): List<EventField<*>> {
    return fold(emptyList()) { acc, field ->
      when (field) {
        is PrimitiveEventField<*> -> acc + field
        is ListEventField<*> -> acc + field
        is ObjectEventField -> acc + field + field.fields.toList().getFieldsRecursively()
        is ObjectListEventField -> acc + field + field.fields.toList().getFieldsRecursively()
      }
    }
  }
}
