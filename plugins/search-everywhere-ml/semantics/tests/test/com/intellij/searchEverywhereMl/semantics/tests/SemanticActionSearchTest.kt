package com.intellij.searchEverywhereMl.semantics.tests

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI.SINGLE_CONTRIBUTOR_ELEMENTS_LIMIT
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.platform.ml.embeddings.search.services.ActionEmbeddingsStorage
import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager
import com.intellij.searchEverywhereMl.semantics.contributors.SemanticActionSearchEverywhereContributor
import com.intellij.searchEverywhereMl.semantics.settings.SearchEverywhereSemanticSettings
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.minutes

class SemanticActionSearchTest : SemanticSearchBaseTestCase() {
  private val storage
    get() = ActionEmbeddingsStorage.getInstance()

  fun `test basic semantics`() = runTest {
    setupTest("java/IndexProjectAction.java") // open file in the editor to make all actions indexable

    var neighbours = storage.searchNeighbours("delete all breakpoints", 10, 0.5).toIdsSet()
    assertContainsElements(neighbours, "Debugger.RemoveAllBreakpoints", "Debugger.RemoveAllBreakpointsInFile")

    neighbours = storage.searchNeighbours("fix ide", 10, 0.5).toIdsSet()
    assertContainsElements(
      neighbours,
      "CallSaul", // 'Repair IDE' action (don't ask why)
      "ExportImportGroup" // 'Manage IDE Settings' action
    )

    neighbours = storage.searchNeighbours("web explorer", 10, 0.5).toIdsSet()
    assertContainsElements(neighbours, "WebBrowser", "BrowseWeb")
  }

  fun `test search everywhere contributor`() = runTest {
    setupTest("java/IndexProjectAction.java")

    val standardActionContributor = ActionSearchEverywhereContributor.Factory()
      .createContributor(createEvent()) as ActionSearchEverywhereContributor
    val searchEverywhereUI = SearchEverywhereUI(project, listOf(SemanticActionSearchEverywhereContributor(standardActionContributor)),
                                                { _ -> null }, null)
    val elements = PlatformTestUtil.waitForFuture(searchEverywhereUI.findElementsForPattern("delete all breakpoints"))

    val items = elements.filterIsInstance<GotoActionModel.MatchedValue>().map { it.value as GotoActionModel.ActionWrapper }.map { it.actionText }

    assertContainsElements(items, "Remove All Breakpoints", "Remove All Breakpoints In The Current File")
  }

  fun `test empty query`() = runTest {
    val semanticActionContributor = SemanticActionSearchEverywhereContributor(
      ActionSearchEverywhereContributor.Factory().createContributor(createEvent()) as ActionSearchEverywhereContributor)

    val semanticSearchEverywhereUI = SearchEverywhereUI(project, listOf(semanticActionContributor))

    val results = PlatformTestUtil.waitForFuture(semanticSearchEverywhereUI.findElementsForPattern(""))

    assertEquals("expected no results from semantic contributor for empty query",
                 0, results.filterIsInstance<GotoActionModel.MatchedValue>().mapNotNull { it.value as? GotoActionModel.MatchedValue }.size)
  }

  fun `test semantic and standard contributor results match`() = runTest(timeout = 2.minutes) {
    setupTest("java/IndexProjectAction.java")

    // Contributors do not share the same GotoActionModel:
    val standardActionContributor = ActionSearchEverywhereContributor.Factory()
      .createContributor(createEvent()) as ActionSearchEverywhereContributor
    val semanticActionContributor = SemanticActionSearchEverywhereContributor(
      ActionSearchEverywhereContributor.Factory().createContributor(createEvent()) as ActionSearchEverywhereContributor)

    val standardSearchEverywhereUI = SearchEverywhereUI(project, listOf(standardActionContributor))
    val semanticSearchEverywhereUI = SearchEverywhereUI(project, listOf(semanticActionContributor))

    val prefixes = ('a'..'z').map { it.toString() }.toMutableList()
    var lastAdded = prefixes.toList()
    repeat(2) {
      lastAdded = lastAdded.flatMap { prefix -> ('a'..'z').map { prefix + it } }
      prefixes.addAll(lastAdded)
    }

    fun findResultsFromUI(ui: SearchEverywhereUI, query: String): List<String> {
      return PlatformTestUtil.waitForFuture(ui.findElementsForPattern(query))
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
    LocalArtifactsManager.getInstance().downloadArtifactsIfNecessary()
    SearchEverywhereSemanticSettings.getInstance().enabledInActionsTab = true
    storage.generateEmbeddingsIfNecessary(project)
  }
}