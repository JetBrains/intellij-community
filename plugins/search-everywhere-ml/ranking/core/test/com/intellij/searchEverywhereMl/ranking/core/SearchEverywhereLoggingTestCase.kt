package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.searchEverywhereMl.MLSE_RECORDER_ID
import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.testFramework.LightPlatformTestCase
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import kotlinx.coroutines.runBlocking

abstract class SearchEverywhereLoggingTestCase : LightPlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  /**
   * Opens a [MockSearchSession] with this provider, runs [testProcedure] in it, and returns the events that the session logs.
   */
  fun MockSearchEverywhereProvider.runSearchAndCollectLogEvents(testProcedure: suspend MockSearchSession.() -> Unit): List<LogEvent> {
    val disposable = Disposer.newDisposable()
    val itemDataFactory = TestItemDataFactory()

    try {
      return FUCollectorTestCase.collectLogEvents(MLSE_RECORDER_ID, disposable, true) {
        runBlocking {
          val searchSession = MockSearchSession(project, this@runSearchAndCollectLogEvents, itemDataFactory)
          searchSession.open()
          testProcedure(searchSession)
        }
      }
    }
    finally {
      SearchEverywhereMlFacade.onSessionFinished()
      itemDataFactory.close()
      Disposer.dispose(disposable)
    }
  }
}

/**
 * Calls [SplitSeMlService] in the order of the Search Everywhere popup with the All tab.
 *
 * Each search gets the elements of the provider, and the search finishes before the next search starts.
 * The session searches for an empty query when it opens, as the popup does.
 */
class MockSearchSession internal constructor(
  private val project: Project,
  private val provider: MockSearchEverywhereProvider,
  private val itemDataFactory: TestItemDataFactory,
) {
  private val mlService = SplitSeMlService()
  private val tabId = SearchEverywhereTab.All.tabId
  private var query = ""
  private var results: List<SeItemData> = emptyList()

  internal suspend fun open() {
    mlService.onSessionStarted(project, tabId)
    search("")
  }

  /** Types [text] one character at a time. Each character starts a new search. */
  suspend fun type(text: CharSequence) {
    text.forEach { character -> search(query + character) }
  }

  /** Selects the first found item. The popup closes after the selection. */
  fun selectFirstItem() {
    mlService.onResultsSelected(listOf(0 to results.first()))
    closePopup()
  }

  fun closePopup() {
    mlService.onSessionFinished()
  }

  private suspend fun search(query: String) {
    this.query = query
    mlService.onStateStarted(tabId, SeParams(query, SeFilterState.Empty))

    results = provider.findElements(query).map { element ->
      val itemData = itemDataFactory.create(element, provider.providerId)
      if (query.isEmpty()) {
        mlService.process(itemData)
        itemData
      }
      else {
        mlService.applyMlWeight(itemData)
      }
    }
    if (results.isNotEmpty()) {
      mlService.notifySearchResultsUpdated()
    }
    mlService.onStateFinished(results)
  }
}

/** A Search Everywhere provider with [providerId] that finds the elements of [findElements] for a query. */
open class MockSearchEverywhereProvider(val providerId: String, val findElements: (query: String) -> List<Any>) {
  /** The actions provider that finds the "registry" element for each query that is not empty. */
  object SingleActionSearchEverywhere : MockSearchEverywhereProvider(SeProviderIdUtils.ACTIONS_ID, { query ->
    if (query.isEmpty()) emptyList() else listOf("registry")
  })
}

internal fun <T : Any> ExtensionPointName<T>.maskedWith(extensions: List<T>): Disposable {
  val disposable = Disposer.newDisposable("ExtensionPointMaskMDisposable for $name")
  (point as ExtensionPointImpl<T>).maskAll(extensions, disposable, false)
  return disposable
}
