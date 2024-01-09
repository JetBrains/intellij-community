package com.intellij.searchEverywhereMl.semantics.tests

import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.util.gotoByName.GotoSymbolModel2
import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.platform.ml.embeddings.search.services.IndexableClass
import com.intellij.platform.ml.embeddings.search.services.SymbolEmbeddingStorage
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.searchEverywhereMl.semantics.contributors.SemanticSymbolSearchEverywhereContributor
import com.intellij.platform.ml.embeddings.search.utils.ScoredText
import com.intellij.platform.ml.embeddings.search.services.SemanticSearchFileChangeListener
import com.intellij.searchEverywhereMl.semantics.settings.SearchEverywhereSemanticSettings
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.utils.editor.commitToPsi
import com.intellij.testFramework.utils.editor.saveToDisk
import com.intellij.testFramework.utils.vfs.deleteRecursively
import com.intellij.util.TimeoutUtil
import kotlinx.coroutines.test.runTest
import org.jetbrains.kotlin.psi.KtFunction


class SemanticSymbolSearchTest : SemanticSearchBaseTestCase() {
  private val storage
    get() = SymbolEmbeddingStorage.getInstance(project)

  private val model
    get() = GotoSymbolModel2(project, testRootDisposable)

  fun `test basic semantics`() = runTest {
    setupTest("java/ProjectIndexingTask.java", "kotlin/ScoresFileManager.kt")
    assertEquals(5, storage.index.size)

    var neighbours = storage.searchNeighbours("begin indexing", 10, 0.5).asSequence().filterByModel()
    assertEquals(setOf("startIndexing", "ProjectIndexingTask"), neighbours)

    neighbours = storage.streamSearchNeighbours("begin indexing", 0.5).filterByModel()
    assertEquals(setOf("startIndexing", "ProjectIndexingTask"), neighbours)

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).filterByModel()
    assertEquals(setOf("handleScoresFile", "clearFileWithScores"), neighbours)
  }

  fun `test index ids are not duplicated`() = runTest {
    setupTest("java/IndexProjectAction.java", "kotlin/IndexProjectAction.kt")
    assertEquals(1, storage.index.size)
  }

  fun `test search everywhere contributor`() = runTest {
    setupTest("java/ProjectIndexingTask.java", "kotlin/ScoresFileManager.kt")
    val searchEverywhereUI = SearchEverywhereUI(project, listOf(SemanticSymbolSearchEverywhereContributor(createEvent())),
                                                { _ -> null }, null)
    val elements = PlatformTestUtil.waitForFuture(searchEverywhereUI.findElementsForPattern("begin indexing"))
    assertEquals(2, elements.size)

    val items: List<PsiElement> = elements.filterIsInstance<PsiItemWithSimilarity<*>>().mapNotNull { extractPsiElement(it) }
    assertEquals(2, items.size)

    val methods = items.filterIsInstance<PsiMethod>().map { IndexableClass(it.name) } +
                  items.filterIsInstance<KtFunction>().map { IndexableClass(it.name ?: "") } +
                  items.filterIsInstance<PsiClass>().map { IndexableClass(it.name ?: "") } // we might have constructors in the results
    assertEquals(2, methods.size)
    assertEquals(setOf("ProjectIndexingTask", "startIndexing"), methods.map { it.id }.toSet())
  }

  fun `test method renaming changes the index`() = runTest {
    setupTest("java/ProjectIndexingTask.java", "kotlin/ScoresFileManager.kt")
    assertEquals(5, storage.index.size)

    var neighbours = storage.streamSearchNeighbours("begin indexing", 0.5).filterByModel()
    assertEquals(setOf("ProjectIndexingTask", "startIndexing"), neighbours)

    neighbours = storage.streamSearchNeighbours("helicopter purchase", 0.5).filterByModel()
    assertEquals(emptySet<String>(), neighbours)

    val nameToReplace = "startIndexing"
    val startOffset = myFixture.editor.document.text.indexOf(nameToReplace)
    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.editor.document.replaceString(startOffset, startOffset + nameToReplace.length, "buyHelicopter")
      myFixture.editor.document.saveToDisk() // This is how we trigger reindexing
      myFixture.editor.document.commitToPsi(project)
    }

    TimeoutUtil.sleep(2000) // wait for two seconds for index update

    neighbours = storage.streamSearchNeighbours("begin indexing", 0.5).filterByModel()
    assertEquals(setOf("ProjectIndexingTask"), neighbours)

    neighbours = storage.streamSearchNeighbours("helicopter purchase", 0.5).filterByModel()
    assertEquals(setOf("buyHelicopter"), neighbours)
  }

  fun `test removal of file with method changes the index`() = runTest {
    setupTest("java/ProjectIndexingTask.java", "kotlin/ScoresFileManager.kt")
    assertEquals(5, storage.index.size)

    var neighbours = storage.streamSearchNeighbours("begin indexing", 0.5).filterByModel()
    assertEquals(setOf("startIndexing", "ProjectIndexingTask"), neighbours)

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).filterByModel()
    assertEquals(setOf("handleScoresFile", "clearFileWithScores"), neighbours)

    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.editor.virtualFile.deleteRecursively() // deletes the currently open file: java/IndexProjectAction.java
    }

    TimeoutUtil.sleep(2000) // wait for two seconds for index update

    neighbours = storage.streamSearchNeighbours("begin indexing", 0.5).filterByModel()
    assertEquals(emptySet<String>(), neighbours)

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).filterByModel()
    assertEquals(setOf("handleScoresFile", "clearFileWithScores"), neighbours)
  }

  private fun Sequence<ScoredText>.filterByModel(): Set<String> {
    return map { it.text }.filter {
      model.getElementsByName(it, false, it).any { element ->
        (element as PsiElement).isValid
      }
    }.toSet()
  }

  private suspend fun setupTest(vararg filePaths: String) {
    SemanticSearchFileChangeListener.getInstance(project).clearEvents()
    myFixture.configureByFiles(*filePaths)
    LocalArtifactsManager.getInstance().downloadArtifactsIfNecessary()
    SearchEverywhereSemanticSettings.getInstance().enabledInSymbolsTab = true
    storage.generateEmbeddingsIfNecessary().join()
    SemanticSearchFileChangeListener.getInstance(project).changeEntityTracking(storage, true)
  }
}