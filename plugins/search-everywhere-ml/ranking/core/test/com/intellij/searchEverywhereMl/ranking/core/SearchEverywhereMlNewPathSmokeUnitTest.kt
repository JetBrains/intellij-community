package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment
import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchStateChangeReason
import com.intellij.testFramework.junit5.TestApplication
import java.util.stream.Stream
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

@TestApplication
internal class SearchEverywhereMlNewPathSmokeUnitTest {
  private val service = SplitSeMlService()
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

  @ParameterizedTest(name = "[{index}] new search smoke tab={0} group={1} run={2}")
  @MethodSource("stressCases")
  fun `new path search smoke does not throw`(tab: SearchEverywhereTab, experimentGroup: Int, iteration: Int) {
    SearchEverywhereMlExperiment.experimentGroupOverride = experimentGroup

    service.onSessionStarted(project = null, tabId = tab.tabId)
    service.onStateStarted(tabId = tab.tabId, searchParams = SeParams("query-$iteration", SeFilterState.Empty))
    service.onStateFinished(results = emptyList())
    service.onSessionFinished()
  }

  @ParameterizedTest(name = "[{index}] new fast-close smoke tab={0} group={1} run={2}")
  @MethodSource("stressCases")
  fun `new path fast-close smoke does not throw`(tab: SearchEverywhereTab, experimentGroup: Int, iteration: Int) {
    SearchEverywhereMlExperiment.experimentGroupOverride = experimentGroup

    service.onSessionStarted(project = null, tabId = tab.tabId)
    service.onStateStarted(tabId = tab.tabId, searchParams = SeParams("close-$iteration", SeFilterState.Empty))
    service.onSessionFinished()
  }

  @Test
  fun `new path onStateStarted throws before session started`() {
    assertThrows<IllegalStateException> {
      service.onStateStarted(tabId = SearchEverywhereTab.Actions.tabId, searchParams = SeParams("guard", SeFilterState.Empty))
    }
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
