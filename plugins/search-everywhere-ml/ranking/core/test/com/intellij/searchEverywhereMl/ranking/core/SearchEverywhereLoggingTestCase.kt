package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchAdapter
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.searchEverywhereMl.log.MLSE_RECORDER_ID
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.SEARCH_RESTARTED
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.SESSION_FINISHED
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import java.util.concurrent.CompletableFuture
import javax.swing.SwingUtilities


abstract class SearchEverywhereLoggingTestCase : LightPlatformTestCase() {
  fun MockSearchEverywhereProvider.runSearchAndCollectLogEvents(testProcedure: SearchEverywhereUI.() -> Unit): List<LogEvent> {
    val emptyDisposable = Disposer.newDisposable()

    return FUCollectorTestCase.collectLogEvents(MLSE_RECORDER_ID, emptyDisposable) {
      val searchEverywhereUI = this.provide(project)

      PlatformTestUtil.waitForAlarm(10)  // wait for rebuild list (session started)

      testProcedure(searchEverywhereUI)

      Disposer.dispose(searchEverywhereUI)  // Otherwise, the instance seems to be reused between different tests
    }.also {
      Disposer.dispose(emptyDisposable)
    }.filter { it.event.id in listOf(SESSION_FINISHED.eventId, SEARCH_RESTARTED.eventId) }
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
}

interface MockSearchEverywhereProvider {
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
