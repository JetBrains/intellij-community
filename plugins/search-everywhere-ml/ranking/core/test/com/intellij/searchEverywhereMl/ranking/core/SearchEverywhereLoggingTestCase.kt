package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchAdapter
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereMlService
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.searchEverywhereMl.MLSE_RECORDER_ID
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import java.util.concurrent.CompletableFuture

private const val SEARCH_TIMEOUT_MS = 10_000L

abstract class SearchEverywhereLoggingTestCase : LightPlatformTestCase() {
  fun MockSearchEverywhereProvider.runSearchAndCollectLogEvents(testProcedure: SearchEverywhereUI.() -> Unit): List<LogEvent> {
    val disposables = mutableListOf<Disposable>()

    try {
      val emptyDisposable = Disposer.newDisposable()
      disposables.add(emptyDisposable)

      return FUCollectorTestCase.collectLogEvents(MLSE_RECORDER_ID, emptyDisposable, true) {
        val searchEverywhereUI = this.provide(project)
        disposables.add(searchEverywhereUI)

        searchEverywhereUI.runAndWaitForSearch("") {
          SearchEverywhereMlService.getInstance()?.onSessionStarted(project,
                                                                    searchEverywhereUI.selectedTabID,
                                                                    searchEverywhereUI.mixedListInfo)
        }

        testProcedure(searchEverywhereUI)
      }
    }
    finally {
      disposables.forEach { Disposer.dispose(it) }
    }
  }

  fun SearchEverywhereUI.type(query: CharSequence) = also { searchEverywhereUI ->
    query.forEach { character ->
      // We are going to add a listener to search finished, so that every character
      // is typed right after the list of results gets updated.
      // Otherwise, we'd typed all characters pretty much at once.
      val expectedPattern = searchEverywhereUI.searchField.text + character
      searchEverywhereUI.runAndWaitForSearch(expectedPattern) {
        searchEverywhereUI.searchField.text = expectedPattern
      }
    }
  }

  private fun SearchEverywhereUI.runAndWaitForSearch(expectedPattern: String, action: () -> Unit) {
    val searchFinished = CompletableFuture<Unit>()
    var expectedSearchStarted = false
    val listener = object : SearchAdapter() {
      override fun searchStarted(pattern: String,
                                 contributors: MutableCollection<out SearchEverywhereContributor<*>>) {
        expectedSearchStarted = pattern == expectedPattern
      }

      override fun searchFinished(items: MutableList<Any>) {
        if (expectedSearchStarted) {
          searchFinished.complete(Unit)
        }
      }
    }

    addSearchListener(listener)
    try {
      action()
      try {
        PlatformTestUtil.waitForFuture(searchFinished, SEARCH_TIMEOUT_MS)
      }
      catch (error: AssertionError) {
        throw AssertionError("Search did not finish for query '$expectedPattern' within $SEARCH_TIMEOUT_MS ms", error)
      }
    }
    finally {
      removeSearchListener(listener)
    }
  }
}

fun interface MockSearchEverywhereProvider {
  fun provide(project: Project): SearchEverywhereUI

  object SingleActionSearchEverywhere : MockSearchEverywhereProvider {
    override fun provide(project: Project): SearchEverywhereUI {
      val contributors = listOf(
        MockSearchEverywhereContributor(ActionSearchEverywhereContributor::class.java.simpleName) { _, _, consumer ->
          consumer.process("registry")
        }
      )

      return SearchEverywhereUI(project, contributors)
    }
  }
}

internal fun <T : Any> ExtensionPointName<T>.maskedWith(extensions: List<T>): Disposable {
  val disposable = Disposer.newDisposable("ExtensionPointMaskMDisposable for $name")
  (point as ExtensionPointImpl<T>).maskAll(extensions, disposable, false)
  return disposable
}
