package com.intellij.searchEverywhereMl.ranking

import com.intellij.ide.actions.searcheverywhere.SearchRestartReason
import com.intellij.searchEverywhereMl.ranking.SearchEverywhereMLStatisticsCollector.REBUILD_REASON_KEY
import com.intellij.searchEverywhereMl.ranking.SearchEverywhereMLStatisticsCollector.SEARCH_RESTARTED
import com.intellij.searchEverywhereMl.ranking.SearchEverywhereMLStatisticsCollector.SESSION_FINISHED
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SearchEverywhereMlStatisticsCollectorTest : SearchEverywhereLoggingTestCase() {
  @Test
  fun `SE open and instant close results in only session finished event`() {
    val events = runSearchEverywhereAndCollectLogEvents {
      sendStatisticsAndClose()
    }

    assertEquals(1, events.size)
    assertTrue(events.first().event.id == SESSION_FINISHED.eventId)
  }

  @Test
  fun `search start event is reported`() {
    val events = runSearchEverywhereAndCollectLogEvents {
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
    val events = runSearchEverywhereAndCollectLogEvents {
      type("reg")
      selectFirst()
    }

    assertNotNull(events.find { it.event.id == SESSION_FINISHED.eventId })
  }

  @Test
  fun `the number of events is correct`() {
    val events = runSearchEverywhereAndCollectLogEvents {
      type("reg")
      selectFirst()
    }

    assertEquals(4, events.size)
  }
}