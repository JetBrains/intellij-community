package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.ide.util.gotoByName.MatchMode
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchResultAdapter
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchResultProviderAdapter
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchStateChangeReason
import com.intellij.searchEverywhereMl.ranking.core.adapters.StateLocalId
import com.intellij.searchEverywhereMl.ranking.core.adapters.TestSearchResultRawAdapter
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
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
  fun `onSessionFinished without search state does not throw`() {
    val session = SearchEverywhereMLSearchSession.createNext(null)
    session.onSessionStarted(actionsTabId, isNewSearchEverywhere = true)

    session.onSessionFinished()

    assertNull(session.activeState)
    assertNull(session.previousSearchState)
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
  fun `onItemsSelected resolves items from finished state`() = runBlocking {
    val session = SearchEverywhereMLSearchSession.createNext(null)
    session.onStateStarted(actionsTabId, "show colr piker", SearchStateChangeReason.SEARCH_START, null, false)
    val correction = SearchEverywhereSpellCheckResult.Correction("show color picker", 0.84)
    val adapter = createCorrectedActionAdapter(correction)

    session.activeState!!.processSearchResult(adapter)
    session.onStateFinished(listOf(adapter))

    session.onItemsSelected(listOf(0 to adapter))
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

  @Test
  fun `split action correction reaches ml features`() = runBlocking {
    val session = SearchEverywhereMLSearchSession.createNext(null)
    session.onStateStarted(actionsTabId, "show colr piker", SearchStateChangeReason.SEARCH_START, null, false)
    val correction = SearchEverywhereSpellCheckResult.Correction("show color picker", 0.84)
    val adapter = createCorrectedActionAdapter(correction)

    val processed = session.activeState!!.processSearchResult(adapter)
    val features = processed.mlFeatures!!.associate { it.field.name to it.data }

    assertEquals(true, features["is_spell_checked"])
    assertEquals(correction.confidence, features["correction_confidence"])
  }

  private fun createCorrectedActionAdapter(correction: SearchEverywhereSpellCheckResult.Correction): SearchResultAdapter.Raw {
    return TestSearchResultRawAdapter(
      provider = SearchResultProviderAdapter.createAdapterFor(SeProviderIdUtils.ACTIONS_ID),
      originalWeight = 42,
      correction = correction,
      stateLocalId = StateLocalId("split-corrected-item"),
      rawItem = createMatchedValue(),
    )
  }

  private fun createMatchedValue(): GotoActionModel.MatchedValue {
    val action = object : AnAction("show color picker") {
      override fun actionPerformed(e: AnActionEvent) {
      }
    }
    val wrapper = GotoActionModel.ActionWrapper(action, null, MatchMode.NAME, Presentation().apply { text = "show color picker" })
    return GotoActionModel.MatchedValue(wrapper, "show color picker", GotoActionModel.MatchedValueType.ACTION)
  }
}
