package com.intellij.searchEverywhereMl.semantics.tests

import com.intellij.searchEverywhereMl.semantics.services.ActionEmbeddingsStorage
import com.intellij.searchEverywhereMl.semantics.services.LocalArtifactsManager
import com.intellij.searchEverywhereMl.semantics.settings.SemanticSearchSettings

class SemanticActionSearchTest : SemanticSearchBaseTestCase() {
  private val storage
    get() = ActionEmbeddingsStorage.getInstance()

  fun `test basic semantics`() {
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

  private fun setupTest(vararg filePaths: String) {
    myFixture.configureByFiles(*filePaths)
    LocalArtifactsManager.getInstance().downloadArtifactsIfNecessary()
    SemanticSearchSettings.getInstance().enabledInActionsTab = true
    storage.generateEmbeddingsIfNecessary()
  }
}