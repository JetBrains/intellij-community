package com.intellij.searchEverywhereMl.ranking

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchAdapter
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereMlContributorReplacementService
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.searchEverywhereMl.log.MLSE_RECORDER_ID
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.concurrency.NonUrgentExecutor
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import java.util.concurrent.CompletableFuture
import javax.swing.SwingUtilities


private fun createSearchEverywhereUI(project: Project): SearchEverywhereUI = SearchEverywhereUI(project, listOf(
  MockSearchEverywhereContributor(ActionSearchEverywhereContributor::class.java.simpleName) { pattern, progressIndicator, consumer ->
    consumer.process("registry")
  }
))

fun runSearchEverywhereLoggingTest(project: Project, testProcedure: SearchEverywhereUI.() -> Unit): List<LogEvent> {
  val emptyDisposable = Disposer.newDisposable()
  Disposer.register(project, emptyDisposable)

  // Otherwise it will attempt to replace the mock contributor,
  // for which we need the search provider id to be ActionsSearchEverywhereContributor
  // to be replaced with SemanticActionSearchEverywhereContributor,
  // that will fail, as it will try to cast the mock contributor to the action's one
  (SearchEverywhereMlContributorReplacementService.EP_NAME.point as ExtensionPointImpl<*>).maskAll(emptyList(), emptyDisposable, false)

  return FUCollectorTestCase.collectLogEvents(MLSE_RECORDER_ID, emptyDisposable) {
    val searchEverywhereUI = createSearchEverywhereUI(project)

    PlatformTestUtil.waitForAlarm(10)  // wait for rebuild list (session started)

    testProcedure(searchEverywhereUI)

    Disposer.dispose(searchEverywhereUI)  // Otherwise, the instance seems to be reused between different tests
    Disposer.dispose(emptyDisposable)

    // Inspired by com.intellij.codeInspection.InspectionApplicationBase.waitForInvokeLaterActivities
    // we wait for these activities. Without this, session-finished event does not get reported in time
    repeat(3) { SwingUtilities.invokeLater { EmptyRunnable.getInstance() } }
    waitForNonUrgentExecutorExecution()  // Wait till the session-finished event is reported
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

fun waitForNonUrgentExecutorExecution() {
  val future = CompletableFuture<Unit>()
  NonUrgentExecutor.getInstance().execute { future.complete(Unit) }
  PlatformTestUtil.waitForFuture(future)
}