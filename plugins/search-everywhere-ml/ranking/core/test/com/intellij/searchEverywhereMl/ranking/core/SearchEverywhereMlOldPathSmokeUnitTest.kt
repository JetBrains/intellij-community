package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.ide.actions.searcheverywhere.SearchRestartReason
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment
import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchStateChangeReason
import com.intellij.testFramework.junit5.TestApplication
import java.util.stream.Stream
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

@TestApplication
internal class SearchEverywhereMlOldPathSmokeUnitTest {
  private val service = SearchEverywhereMlRankingService()
  private val actionsTabId = SearchEverywhereTab.Actions.tabId

  private var originalExperimentalMode: Boolean = false
  private var originalExperimentGroupOverride: Int? = null

  @BeforeEach
  fun setUp() {
    originalExperimentalMode = SearchEverywhereMlExperiment.isExperimentalMode
    originalExperimentGroupOverride = SearchEverywhereMlExperiment.experimentGroupOverride
    SearchEverywhereMlExperiment.isExperimentalMode = true
    cleanUpFacade()
  }

  @AfterEach
  fun tearDown() {
    SearchEverywhereMlExperiment.isExperimentalMode = originalExperimentalMode
    SearchEverywhereMlExperiment.experimentGroupOverride = originalExperimentGroupOverride
    cleanUpFacade()
  }

  @ParameterizedTest(name = "[{index}] old search smoke tab={0} group={1} run={2}")
  @MethodSource("stressCases")
  fun `old path search smoke does not throw`(tab: SearchEverywhereTab, experimentGroup: Int, iteration: Int) {
    SearchEverywhereMlExperiment.experimentGroupOverride = experimentGroup

    startLegacySession(tab.tabId)
    service.onStateStarted(
      tabId = tab.tabId,
      reason = SearchRestartReason.SEARCH_STARTED,
      searchQuery = "query-$iteration",
      searchScope = null,
      isSearchEverywhere = true,
    )

    val contributor = MockSearchEverywhereContributor("old-smoke-provider-$iteration")
    val result = service.createFoundElementInfo(
      contributor = contributor,
      element = "old-element-$iteration",
      priority = 100,
      correction = SearchEverywhereSpellCheckResult.NoCorrection,
    )

    service.onStateFinished(results = listOf(result))
    service.onSessionFinished()
  }

  @ParameterizedTest(name = "[{index}] old fast-close smoke tab={0} group={1} run={2}")
  @MethodSource("stressCases")
  fun `old path fast-close smoke does not throw`(tab: SearchEverywhereTab, experimentGroup: Int, iteration: Int) {
    SearchEverywhereMlExperiment.experimentGroupOverride = experimentGroup

    startLegacySession(tab.tabId)
    service.onStateStarted(
      tabId = tab.tabId,
      reason = SearchRestartReason.SEARCH_STARTED,
      searchQuery = "close-$iteration",
      searchScope = null,
      isSearchEverywhere = true,
    )
    service.onDialogClose()
  }

  @Test
  fun `old path createFoundElementInfo falls back before session started`() {
    val contributor = MockSearchEverywhereContributor("old-guard-provider")
    assertNull(SearchEverywhereMlFacade.activeSession)
    val result = assertDoesNotThrow {
      service.createFoundElementInfo(
        contributor = contributor,
        element = "guard-element",
        priority = 100,
        correction = SearchEverywhereSpellCheckResult.NoCorrection,
      )
    }

    assertEquals(100, result.priority)
    assertEquals("guard-element", result.element)
    assertSame(contributor, result.contributor)
    assertSame(SearchEverywhereSpellCheckResult.NoCorrection, result.correction)
    assertNull(SearchEverywhereMlFacade.activeSession)
  }

  private fun startLegacySession(tabId: String) {
    SearchEverywhereMlFacade.onSessionStarted(project = null, tabId = tabId, isNewSearchEverywhere = false)
  }

  private fun cleanUpFacade() {
    val session = SearchEverywhereMlFacade.activeSession ?: return
    if (session.activeState == null && session.previousSearchState == null) {
      SearchEverywhereMlFacade.onStateStarted(actionsTabId, "", SearchStateChangeReason.SEARCH_START, null, false)
      SearchEverywhereMlFacade.onStateFinished(emptyList())
    }
    SearchEverywhereMlFacade.onSessionFinished()
  }

  private companion object {
    private const val STRESS_REPEATS = 5

    @JvmStatic
    fun stressCases(): Stream<Arguments> {
      val tabs = listOf(
        SearchEverywhereTab.All,
        SearchEverywhereTab.Classes,
        SearchEverywhereTab.Files,
        SearchEverywhereTab.Symbols,
        SearchEverywhereTab.Actions,
      )
      val groups = (0 until SearchEverywhereMlExperiment.NUMBER_OF_GROUPS).toList()
      return tabs
        .flatMap { tab ->
          groups.flatMap { group ->
            (1..STRESS_REPEATS).map { iteration ->
              Arguments.of(tab, group, iteration)
            }
          }
        }
        .stream()
    }
  }
}
