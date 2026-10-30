package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment
import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchResultAdapter
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchResultProviderAdapter
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchStateChangeReason
import com.intellij.searchEverywhereMl.ranking.core.adapters.StateLocalId
import com.intellij.searchEverywhereMl.ranking.core.adapters.TestSearchResultRawAdapter
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
internal class SearchEverywhereMlFacadeTest {
  private val actionsTabId = SearchEverywhereTab.Actions.tabId
  private var originalExperimentalMode: Boolean = false

  @BeforeEach
  fun setUp() {
    originalExperimentalMode = SearchEverywhereMlExperiment.isExperimentalMode
    cleanUpFacade()
  }

  @AfterEach
  fun tearDown() {
    SearchEverywhereMlExperiment.isExperimentalMode = originalExperimentalMode
    cleanUpFacade()
  }

  private fun cleanUpFacade() {
    SearchEverywhereMlFacade.onSessionFinished()
  }

  @Test
  fun `activeSession is null before start`() {
    assertNull(SearchEverywhereMlFacade.activeSession)
  }

  @Test
  fun `onSessionStarted creates session when ML enabled`() {
    SearchEverywhereMlExperiment.isExperimentalMode = true
    assumeTrue(SearchEverywhereMlFacade.isMlEnabled, "ML must be enabled for this test")
    SearchEverywhereMlFacade.onSessionStarted(null, actionsTabId, isNewSearchEverywhere = true)

    assertNotNull(SearchEverywhereMlFacade.activeSession)
  }

  @Test
  fun `onSessionStarted does not create session when ML disabled`() {
    SearchEverywhereMlExperiment.isExperimentalMode = false
    assumeFalse(SearchEverywhereMlFacade.isMlEnabled, "ML must be disabled for this test (tab-level ML ranking may override)")
    SearchEverywhereMlFacade.onSessionStarted(null, actionsTabId, isNewSearchEverywhere = true)

    assertNull(SearchEverywhereMlFacade.activeSession)
  }

  @Test
  fun `onSessionFinished clears active session`() {
    SearchEverywhereMlExperiment.isExperimentalMode = true
    assumeTrue(SearchEverywhereMlFacade.isMlEnabled, "ML must be enabled for this test")
    SearchEverywhereMlFacade.onSessionStarted(null, actionsTabId, isNewSearchEverywhere = true)
    assertNotNull(SearchEverywhereMlFacade.activeSession)

    SearchEverywhereMlFacade.onStateStarted(actionsTabId, "q", SearchStateChangeReason.SEARCH_START, null, false)
    SearchEverywhereMlFacade.onStateFinished(emptyList())
    SearchEverywhereMlFacade.onSessionFinished()

    assertNull(SearchEverywhereMlFacade.activeSession)
  }

  @Test
  fun `processSearchResult throws when no session`() {
    assertNull(SearchEverywhereMlFacade.activeSession)
    val adapter = createRawAdapter()

    assertThrows(IllegalStateException::class.java) {
      SearchEverywhereMlFacade.processSearchResult(adapter)
    }
  }

  @Test
  fun `processSearchResult throws when no active state`() {
    SearchEverywhereMlExperiment.isExperimentalMode = true
    assumeTrue(SearchEverywhereMlFacade.isMlEnabled, "ML must be enabled for this test")
    SearchEverywhereMlFacade.onSessionStarted(null, actionsTabId, isNewSearchEverywhere = true)

    val adapter = createRawAdapter()

    assertThrows(IllegalStateException::class.java) {
      SearchEverywhereMlFacade.processSearchResult(adapter)
    }
  }

  private fun createRawAdapter(): SearchResultAdapter.Raw {
    return TestSearchResultRawAdapter(
      provider = SearchResultProviderAdapter.createAdapterFor("testId"),
      originalWeight = 100,
      stateLocalId = StateLocalId("uuid-1"),
      rawItem = "element",
    )
  }
}
