@file:OptIn(IntellijInternalApi::class)

package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.actions.searcheverywhere.SearchRestartReason
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.REBUILD_REASON_KEY
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.SEARCH_RESTARTED
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.SESSION_DURATION
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.SESSION_FINISHED
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SearchEverywhereMlStatisticsCollectorTest : SearchEverywhereLoggingTestCase() {
  @Test
  fun `SE open and instant close results in only session finished event`() {
    val events = MockSearchEverywhereProvider.SingleActionSearchEverywhere.runSearchAndCollectLogEvents {
      sendStatisticsAndClose()
    }

    assertEquals(1, events.size)
    assertTrue(events.first().event.id == SESSION_FINISHED.eventId)
  }

  @Test
  fun `search start event is reported`() {
    val events = MockSearchEverywhereProvider.SingleActionSearchEverywhere.runSearchAndCollectLogEvents {
      // we need to type at least one character, otherwise opening and closing SE
      // will result in just a single session-finished event
      type("r")
      sendStatisticsAndClose()
    }

    assertNotNull(events.filter { it.event.id == SEARCH_RESTARTED.eventId }
                    .find { it.event.data[REBUILD_REASON_KEY.name] == SearchRestartReason.SEARCH_STARTED.toString() })
  }

  @Test
  fun `search finished event is reported`() {
    val events = MockSearchEverywhereProvider.SingleActionSearchEverywhere.runSearchAndCollectLogEvents {
      type("reg")
      selectFirstItem()
    }

    assertNotNull(events.find { it.event.id == SESSION_FINISHED.eventId })
  }

  @Test
  fun `the number of events is correct`() {
    val events = MockSearchEverywhereProvider.SingleActionSearchEverywhere.runSearchAndCollectLogEvents {
      type("reg")
      selectFirstItem()
    }

    assertEquals(4, events.size)
  }

  @Test
  fun `session duration is reported on item selection that closes the popup`() {
    val events = MockSearchEverywhereProvider.SingleActionSearchEverywhere.runSearchAndCollectLogEvents {
      type("reg")
      selectFirstItem()
    }

    val sessionFinishedEvents = events.filter { it.event.id == SESSION_FINISHED.eventId }
    val lastSessionFinishedEvent = sessionFinishedEvents.last()
    assertTrue("SESSION_FINISHED event that closes a popup should have Search Everywhere session duration",
               SESSION_DURATION.name in lastSessionFinishedEvent.event.data)
  }

  @Test
  fun `session duration is reported only when popup closes`() {
    // We will create a provider with a contributor that does not close the popup on item selection
    val provider = MockSearchEverywhereProvider { project ->
      SearchEverywhereUI(project, listOf(
        MockSearchEverywhereContributor(ActionSearchEverywhereContributor::class.java.simpleName, false) { _, _, consumer ->
          consumer.process("registry")
        }
      ))
    }

    val events = provider.runSearchAndCollectLogEvents {
      type("reg")
      selectFirstItem()  // This will not close the popup

      sendStatisticsAndClose()
    }

    assertEquals("Expected 5 events (3 SEARCH_RESTARTED, 1 SESSION_FINISHED on selection, 1 SESSION_FINISH on popup close)",
                 5, events.size)

    val sessionFinishedEvents = events.filter { it.event.id == SESSION_FINISHED.eventId }
    assertEquals("Expected 2 SESSION_FINISHED events (1 selection + 1 popup close)",
                 2, sessionFinishedEvents.size)

    // Check that the 2nd last SESSION_FINISHED event does NOT have the session duration reported
    val secondLastSessionFinishedEvent = sessionFinishedEvents.first()
    assertFalse("SESSION_FINISHED event that does not close a popup should NOT have Search Everywhere session duration",
                SESSION_DURATION.name in secondLastSessionFinishedEvent.event.data)

    // Check that the last SESSION_FINISHED event has the session duration reported
    val lastSessionFinishedEvent = sessionFinishedEvents.last()
    assertTrue("SESSION_FINISHED event that closes a popup should have Search Everywhere session duration",
               SESSION_DURATION.name in lastSessionFinishedEvent.event.data)
  }
}