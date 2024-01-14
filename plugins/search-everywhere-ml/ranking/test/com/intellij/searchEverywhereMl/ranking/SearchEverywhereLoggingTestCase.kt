package com.intellij.searchEverywhereMl.ranking

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchAdapter
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereMlContributorReplacement
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.searchEverywhereMl.log.MLSE_RECORDER_ID
import com.intellij.searchEverywhereMl.ranking.SearchEverywhereMLStatisticsCollector.SEARCH_RESTARTED
import com.intellij.searchEverywhereMl.ranking.SearchEverywhereMLStatisticsCollector.SESSION_FINISHED
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import java.util.concurrent.CompletableFuture
import javax.swing.SwingUtilities


abstract class SearchEverywhereLoggingTestCase : LightPlatformTestCase() {
  private val extensionPointMaskManager = ExtensionPointMaskManager()

  private fun createSearchEverywhereUI(project: Project): SearchEverywhereUI = SearchEverywhereUI(project, listOf(
    MockSearchEverywhereContributor(ActionSearchEverywhereContributor::class.java.simpleName) { pattern, progressIndicator, consumer ->
      consumer.process("registry")
    }
  ))

  fun runSearchEverywhereAndCollectLogEvents(testProcedure: SearchEverywhereUI.() -> Unit): List<LogEvent> {
    return runSearchEverywhereAndCollectLogEvents(::createSearchEverywhereUI, testProcedure)
  }

  fun runSearchEverywhereAndCollectLogEvents(searchEverywhereUIProvider: (Project) -> SearchEverywhereUI,
                                             testProcedure: SearchEverywhereUI.() -> Unit): List<LogEvent> {
    maskContributorReplacementService()

    extensionPointMaskManager.mask()
    val result = performTest(searchEverywhereUIProvider, testProcedure)
    extensionPointMaskManager.dispose()

    return result.filter { it.event.id in listOf(SESSION_FINISHED.eventId, SEARCH_RESTARTED.eventId) }
  }

  private fun performTest(searchEverywhereUIProvider: (Project) -> SearchEverywhereUI,
                          testProcedure: SearchEverywhereUI.() -> Unit): List<LogEvent> {
    val emptyDisposable = Disposer.newDisposable()

    return FUCollectorTestCase.collectLogEvents(MLSE_RECORDER_ID, emptyDisposable) {
      val searchEverywhereUI = searchEverywhereUIProvider.invoke(project)

      PlatformTestUtil.waitForAlarm(10)  // wait for rebuild list (session started)

      testProcedure(searchEverywhereUI)

      Disposer.dispose(searchEverywhereUI)  // Otherwise, the instance seems to be reused between different tests
    }.also {
      Disposer.dispose(emptyDisposable)
    }
  }

  fun SearchEverywhereUI.type(query: CharSequence) = also { searchEverywhereUI ->
    query.forEach { character ->
      // We are going to add a listener to search finished, so that every character
      // is typed right after the list of results gets updated.
      // Otherwise, we'd typed all characters pretty much at once.
      val future = CompletableFuture<Unit>()
      searchEverywhereUI.addSearchListener(object : SearchAdapter() {
        override fun searchFinished(items: MutableList<Any>) {
          future.complete(Unit)
          SwingUtilities.invokeLater { searchEverywhereUI.removeSearchListener(this) }
        }
      })

      searchEverywhereUI.searchField.text += character
      PlatformTestUtil.waitForFuture(future)
    }
  }

  private fun maskContributorReplacementService() {
    // Otherwise it will attempt to replace the mock contributor,
    // for which we need the search provider id to be ActionsSearchEverywhereContributor
    // to be replaced with SemanticActionSearchEverywhereContributor,
    // that will fail, as it will try to cast the mock contributor to the action's one
    SearchEverywhereMlContributorReplacement.EP_NAME.point.maskForSingleTest(emptyList())
  }

  protected fun <V : Any> ExtensionPoint<V>.maskForSingleTest(newList: List<V>) {
    extensionPointMaskManager.addServiceToMask(this, newList)
  }

  private class ExtensionPointMaskManager {
    private val serviceMaskDisposable = Disposer.newDisposable()

    private val servicesToMask = mutableListOf<() -> Unit>()

    fun <T : Any> addServiceToMask(ep: ExtensionPoint<T>, maskWith: List<T>) {
      val maskingFunction = { (ep as ExtensionPointImpl<T>).maskAll(maskWith, serviceMaskDisposable, false) }
      servicesToMask.add(maskingFunction)
    }

    fun mask() {
      servicesToMask.forEach { it.invoke() }
    }

    fun dispose() {
      Disposer.dispose(serviceMaskDisposable)
      servicesToMask.clear()
    }
  }
}
