package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchResultAdapter
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchStateChangeReason
import com.intellij.searchEverywhereMl.ranking.core.adapters.StateLocalId
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class SearchEverywhereMLSearchSessionTest {
  private val actionsTabId = SearchEverywhereTab.Actions.tabId
  private val allTabId = SearchEverywhereTab.All.tabId

  @AfterEach
  fun tearDown() {
    SearchEverywhereMlFacade.onSessionFinished()
  }

  @Test
  fun `createNext increments session id`() {
    val session1 = SearchEverywhereMLSearchSession.createNext(null)
    val session2 = SearchEverywhereMLSearchSession.createNext(null)
    assertTrue(session2.sessionId > session1.sessionId)
  }

  @Test
  fun `activeState is null initially`() {
    val session = SearchEverywhereMLSearchSession.createNext(null)
    assertNull(session.activeState)
  }

  @Test
  fun `onStateStarted creates active state`() {
    val session = SearchEverywhereMLSearchSession.createNext(null)
    session.onStateStarted(actionsTabId, "query", SearchStateChangeReason.SEARCH_START, null, false)

    assertNotNull(session.activeState)
  }

  @Test
  fun `onStateStarted first state always gets SEARCH_START`() {
    val session = SearchEverywhereMLSearchSession.createNext(null)
    session.onStateStarted(actionsTabId, "query", SearchStateChangeReason.QUERY_CHANGE, null, false)

    assertEquals(SearchStateChangeReason.SEARCH_START, session.activeState!!.searchStateChangeReason)
  }

  @Test
  fun `onStateStarted interrupts previous unfinished state`() {
    val session = SearchEverywhereMLSearchSession.createNext(null)
    session.onStateStarted(actionsTabId, "q1", SearchStateChangeReason.SEARCH_START, null, false)
    session.onStateStarted(actionsTabId, "q2", SearchStateChangeReason.QUERY_CHANGE, null, false)

    val interruptedState = session.previousSearchState
    assertNotNull(interruptedState)
    assertTrue(interruptedState!!.wasInterrupted)
    assertTrue(interruptedState.isFinished)
  }

  @Test
  fun `onStateFinished clears active state`() {
    val session = SearchEverywhereMLSearchSession.createNext(null)
    session.onStateStarted(actionsTabId, "query", SearchStateChangeReason.SEARCH_START, null, false)
    session.onStateFinished(emptyList())

    assertNull(session.activeState)
  }

  @Test
  fun `onStateFinished duplicate call is no-op`() {
    val session = SearchEverywhereMLSearchSession.createNext(null)
    session.onStateStarted(actionsTabId, "query", SearchStateChangeReason.SEARCH_START, null, false)
    session.onStateFinished(emptyList())
    session.onStateFinished(emptyList()) // should not throw
    assertNull(session.activeState)
  }

  @Test
  fun `previousSearchState returns null initially`() {
    val session = SearchEverywhereMLSearchSession.createNext(null)
    assertNull(session.previousSearchState)
  }

  @Test
  fun `previousSearchState returns last finished state`() {
    val session = SearchEverywhereMLSearchSession.createNext(null)
    session.onStateStarted(actionsTabId, "query1", SearchStateChangeReason.SEARCH_START, null, false)
    session.onStateFinished(emptyList())

    val previous = session.previousSearchState
    assertNotNull(previous)
    assertEquals("query1", previous!!.query)
  }

  @Test
  fun `state index increments across states`() {
    val session = SearchEverywhereMLSearchSession.createNext(null)

    session.onStateStarted(actionsTabId, "q1", SearchStateChangeReason.SEARCH_START, null, false)
    val firstIndex = session.activeState!!.index
    session.onStateFinished(emptyList())

    session.onStateStarted(actionsTabId, "q2", SearchStateChangeReason.QUERY_CHANGE, null, false)
    val secondIndex = session.activeState!!.index

    assertTrue(secondIndex > firstIndex)
  }

  @Test
  fun `SearchState markAsFinished sets flag`() {
    val session = SearchEverywhereMLSearchSession.createNext(null)
    session.onStateStarted(actionsTabId, "query", SearchStateChangeReason.SEARCH_START, null, false)
    val state = session.activeState!!

    assertFalse(state.isFinished)
    state.markAsFinished()
    assertTrue(state.isFinished)
  }

  @Test
  fun `SearchState markAsInterrupted sets both flags`() {
    val session = SearchEverywhereMLSearchSession.createNext(null)
    session.onStateStarted(actionsTabId, "query", SearchStateChangeReason.SEARCH_START, null, false)
    val state = session.activeState!!

    state.markAsInterrupted()
    assertTrue(state.wasInterrupted)
    assertTrue(state.isFinished)
  }

  @Test
  fun `SearchState getCachedResults returns empty initially`() {
    val session = SearchEverywhereMLSearchSession.createNext(null)
    session.onStateStarted(actionsTabId, "query", SearchStateChangeReason.SEARCH_START, null, false)
    val state = session.activeState!!

    assertTrue(state.getCachedResults().isEmpty())
  }

  @Test
  fun `SearchState getProcessedResultByIdOrNull returns null for unknown`() {
    val session = SearchEverywhereMLSearchSession.createNext(null)
    session.onStateStarted(actionsTabId, "query", SearchStateChangeReason.SEARCH_START, null, false)
    val state = session.activeState!!

    assertNull(state.getProcessedResultByIdOrNull(StateLocalId("nonexistent")))
  }

  @Test
  fun `SearchState getProcessedSearchResultById throws for unknown`() {
    val session = SearchEverywhereMLSearchSession.createNext(null)
    session.onStateStarted(actionsTabId, "query", SearchStateChangeReason.SEARCH_START, null, false)
    val state = session.activeState!!

    assertThrows(IllegalStateException::class.java) {
      state.getProcessedSearchResultById(StateLocalId("nonexistent"))
    }
  }

  @Test
  fun `onItemsSelected with empty list does not throw`() {
    val session = SearchEverywhereMLSearchSession.createNext(null)
    session.onStateStarted(actionsTabId, "query", SearchStateChangeReason.SEARCH_START, null, false)
    session.onItemsSelected(emptyList())
  }

  @Test
  fun `onItemsSelected throws when items not in any state`() {
    val session = SearchEverywhereMLSearchSession.createNext(null)
    session.onStateStarted(actionsTabId, "query", SearchStateChangeReason.SEARCH_START, null, false)

    val contributor = MockSearchEverywhereContributor("testId")
    val elementInfo = SearchEverywhereFoundElementInfo("uuid-1", "element", 100, contributor)
    val adapter = SearchResultAdapter.createAdapterFor(elementInfo)

    assertThrows(IllegalStateException::class.java) {
      session.onItemsSelected(listOf(0 to adapter))
    }
  }

  @Test
  fun `orderByMl false when tab has no ML ranking`() {
    val session = SearchEverywhereMLSearchSession.createNext(null)
    session.onStateStarted("UnknownTabWithNoMlRanking", "query", SearchStateChangeReason.SEARCH_START, null, false)

    assertFalse(session.activeState!!.orderByMl)
  }

  @Test
  fun `orderByMl false when query empty on All tab`() {
    val session = SearchEverywhereMLSearchSession.createNext(null)
    session.onStateStarted(allTabId, "", SearchStateChangeReason.SEARCH_START, null, false)

    assertFalse(session.activeState!!.orderByMl)
  }

  @Test
  fun `orderByMl false when query empty on Actions tab`() {
    val session = SearchEverywhereMLSearchSession.createNext(null)
    session.onStateStarted(actionsTabId, "", SearchStateChangeReason.SEARCH_START, null, false)

    assertFalse(session.activeState!!.orderByMl)
  }
}
