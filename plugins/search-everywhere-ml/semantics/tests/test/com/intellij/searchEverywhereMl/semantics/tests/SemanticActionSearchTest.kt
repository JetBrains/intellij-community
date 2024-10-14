package com.intellij.searchEverywhereMl.semantics.tests

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI.SINGLE_CONTRIBUTOR_ELEMENTS_LIMIT
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.Disposer
import com.intellij.ml.llm.embeddings.core.actions.ActionEmbeddingStorageManager
import com.intellij.ml.llm.embeddings.core.indexer.IndexId
import com.intellij.ml.llm.embeddings.core.indexer.configuration.EmbeddingsConfiguration
import com.intellij.ml.llm.embeddings.searchEverywhere.contributors.SemanticActionSearchEverywhereContributor
import com.intellij.ml.llm.embeddings.searchEverywhere.settings.SearchEverywhereSemanticSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SemanticActionSearchTest : SemanticSearchBaseTestCase() {
  private val storageWrapper
    get() = EmbeddingsConfiguration.getStorageManagerWrapper(IndexId.ACTIONS)

  fun `test basic semantics`() = runTest {
    setupTest("java/IndexProjectAction.java") // open file in the editor to make all actions indexable

    var neighbours = storageWrapper.search(null, "delete all breakpoints", 10, 0.5f).map { it.id }.toSet()
    assertContainsElements(neighbours, "Debugger.RemoveAllBreakpoints", "Debugger.RemoveAllBreakpointsInFile")

    neighbours = storageWrapper.search(null, "fix ide", 10, 0.5f).map { it.id }.toSet()
    assertContainsElements(
      neighbours,
      "CallSaul", // 'Repair IDE' action (don't ask why)
      "ExportImportGroup" // 'Manage IDE Settings' action
    )

    neighbours = storageWrapper.search(null, "web explorer", 10, 0.5f).map { it.id }.toSet()
    assertContainsElements(neighbours, "WebBrowser", "BrowseWeb")
  }

  fun `test search everywhere contributor`() = runTest(
    timeout = 1.minutes // time to generate action embeddings
  ) {
    setupTest("java/IndexProjectAction.java")

    val standardActionContributor = ActionSearchEverywhereContributor.Factory()
      .createContributor(createEvent()) as ActionSearchEverywhereContributor
    val searchEverywhereUI = runBlocking(Dispatchers.EDT) {
      SearchEverywhereUI(project, listOf(SemanticActionSearchEverywhereContributor(standardActionContributor)), { _ -> null }, null)
    }
    Disposer.register(project, searchEverywhereUI)

    val elements = runOnEdt { searchEverywhereUI.findElementsForPattern("delete all breakpoints") }.await()

    val items = elements.filterIsInstance<GotoActionModel.MatchedValue>().map { (it.value as GotoActionModel.ActionWrapper).actionText }

    assertContainsElements(items, "Remove All Breakpoints", "Remove All Breakpoints In The Current File")
  }

  fun `test empty query`() = runTest(timeout = 10.seconds) {
    val semanticActionContributor = readAction {
      SemanticActionSearchEverywhereContributor(
        ActionSearchEverywhereContributor.Factory().createContributor(createEvent()) as ActionSearchEverywhereContributor)
    }

    val semanticSearchEverywhereUI = runBlocking(Dispatchers.EDT) { SearchEverywhereUI(project, listOf(semanticActionContributor)) }
    Disposer.register(project, semanticSearchEverywhereUI)

    val results = runOnEdt { semanticSearchEverywhereUI.findElementsForPattern("") }.await()

    assertEquals("expected no results from semantic contributor for empty query",
                 0, results.filterIsInstance<GotoActionModel.MatchedValue>().mapNotNull { it.value as? GotoActionModel.MatchedValue }.size)
  }

  fun `test semantic and standard contributor results match`() = runTest(timeout = 5.minutes) {
    setupTest("java/IndexProjectAction.java")

    // Contributors do not share the same GotoActionModel:
    val standardActionContributor = readAction { ActionSearchEverywhereContributor.Factory()
      .createContributor(createEvent()) as ActionSearchEverywhereContributor }
    val semanticActionContributor = readAction {
      SemanticActionSearchEverywhereContributor(
        ActionSearchEverywhereContributor.Factory().createContributor(createEvent()) as ActionSearchEverywhereContributor)
    }

    val standardSearchEverywhereUI = runBlocking(Dispatchers.EDT) { SearchEverywhereUI(project, listOf(standardActionContributor)) }
    Disposer.register(project, standardSearchEverywhereUI)
    val semanticSearchEverywhereUI = runBlocking(Dispatchers.EDT) { SearchEverywhereUI(project, listOf(semanticActionContributor)) }
    Disposer.register(project, semanticSearchEverywhereUI)

    val prefixes = ('a'..'z').map { it.toString() }.toMutableList()
    var lastAdded = prefixes.toList()
    repeat(2) {
      lastAdded = lastAdded.flatMap { prefix -> ('a'..'z').map { prefix + it } }
      prefixes.addAll(lastAdded)
    }

    suspend fun findResultsFromUI(ui: SearchEverywhereUI, query: String): List<String> {
      return runOnEdt { ui.findElementsForPattern(query) }.await()
        .filterIsInstance<GotoActionModel.MatchedValue>()
        .mapNotNull { it.value as? GotoActionModel.ActionWrapper }
        // 'Include disabled actions' checkbox is automatically set in standard search when no results found.
        // Since there are fewer cases when lookup is empty, disabled actions might not appear in semantic search results, and that's fine:
        .filter { it.isAvailable }
        .map { it.actionText }
    }

    val iterations = 1200
    for (query in prefixes.take(iterations)) {
      val semanticResults = findResultsFromUI(semanticSearchEverywhereUI, query)
      val standardResults = findResultsFromUI(standardSearchEverywhereUI, query)

      assert(standardResults.toSet().minus(semanticResults.toSet()).isEmpty() ||
             (standardResults.size == semanticResults.size && semanticResults.size == SINGLE_CONTRIBUTOR_ELEMENTS_LIMIT)) {
        """
          Not all elements from standard contributor are present in semantic contributor results
          query: $query
          standard results (len = ${standardResults.size}): $standardResults
          semantic results (len = ${semanticResults.size}): $semanticResults
          removed results: ${standardResults.toSet().minus(semanticResults.toSet())}
          added results: ${semanticResults.toSet().minus(standardResults.toSet())}
        """.trimIndent()
      }
    }
  }

  private suspend fun setupTest(vararg filePaths: String) {
    myFixture.configureByFiles(*filePaths)
    SearchEverywhereSemanticSettings.getInstance().enabledInActionsTab = true
    storageWrapper.clearStorage(null)
    ActionEmbeddingStorageManager.getInstance().prepareForSearch().join()
  }
}