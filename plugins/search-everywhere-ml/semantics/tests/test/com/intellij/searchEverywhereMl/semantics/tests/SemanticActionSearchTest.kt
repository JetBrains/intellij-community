package com.intellij.searchEverywhereMl.semantics.tests

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.platform.ml.embeddings.search.services.ActionEmbeddingsStorage
import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager
import com.intellij.searchEverywhereMl.semantics.contributors.SemanticActionSearchEverywhereContributor
import com.intellij.platform.ml.embeddings.search.settings.SemanticSearchSettings
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.test.runTest

class SemanticActionSearchTest : SemanticSearchBaseTestCase() {
  private val storage
    get() = ActionEmbeddingsStorage.getInstance()

  fun `test basic semantics`() = runTest {
    setupTest("java/IndexProjectAction.java") // open file in the editor to make all actions indexable

    var neighbours = storage.searchNeighboursIfEnabled("delete all breakpoints", 10, 0.5).toIdsSet()
    assertContainsElements(neighbours, "Debugger.RemoveAllBreakpoints", "Debugger.RemoveAllBreakpointsInFile")

    neighbours = storage.searchNeighboursIfEnabled("fix ide", 10, 0.5).toIdsSet()
    assertContainsElements(
      neighbours,
      "CallSaul", // 'Repair IDE' action (don't ask why)
      "ExportImportGroup" // 'Manage IDE Settings' action
    )

    neighbours = storage.searchNeighboursIfEnabled("web explorer", 10, 0.5).toIdsSet()
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

  private suspend fun setupTest(vararg filePaths: String) {
    myFixture.configureByFiles(*filePaths)
    LocalArtifactsManager.getInstance().downloadArtifactsIfNecessary()
    SemanticSearchSettings.getInstance().enabledInActionsTab = true
    storage.generateEmbeddingsIfNecessary(project)
  }
}