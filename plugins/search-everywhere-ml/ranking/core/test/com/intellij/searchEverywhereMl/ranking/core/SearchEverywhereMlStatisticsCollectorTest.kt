@file:OptIn(IntellijInternalApi::class)

package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.SearchRestartReason
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.REBUILD_REASON_KEY
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.SEARCH_RESTARTED
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
}