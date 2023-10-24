package com.intellij.searchEverywhereMl.semantics.tests

import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.platform.ml.embeddings.search.services.IndexableClass
import com.intellij.platform.ml.embeddings.search.services.SemanticSearchFileContentListener
import com.intellij.platform.ml.embeddings.search.services.SymbolEmbeddingStorage
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.searchEverywhereMl.semantics.contributors.SemanticSymbolSearchEverywhereContributor
import com.intellij.platform.ml.embeddings.search.settings.SemanticSearchSettings
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.utils.editor.saveToDisk
import com.intellij.testFramework.utils.vfs.deleteRecursively
import com.intellij.util.TimeoutUtil
import kotlinx.coroutines.test.runTest
import org.jetbrains.kotlin.psi.KtFunction


class SemanticSymbolSearchTest : SemanticSearchBaseTestCase() {
  private val storage
    get() = SymbolEmbeddingStorage.getInstance(project)

  fun `test basic semantics`() = runTest {
    setupTest("java/ProjectIndexingTask.java", "kotlin/ScoresFileManager.kt")
    assertEquals(5, storage.index.size)

    var neighbours = storage.searchNeighboursIfEnabled("begin indexing", 10, 0.5)
    assertEquals(setOf("startIndexing", "ProjectIndexingTask"), neighbours.map { it.text }.toSet())

    neighbours = storage.streamSearchNeighbours("begin indexing", 0.5).toList()
    assertEquals(setOf("startIndexing", "ProjectIndexingTask"), neighbours.map { it.text }.toSet())

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).toList()
    assertEquals(setOf("handleScoresFile", "clearFileWithScores"), neighbours.map { it.text }.toSet())
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

    var neighbours = storage.streamSearchNeighbours("begin indexing", 0.5).toList()
    assertEquals(setOf("ProjectIndexingTask", "startIndexing"), neighbours.map { it.text }.toSet())

    neighbours = storage.streamSearchNeighbours("helicopter purchase", 0.5).toList()
    assertEquals(emptySet<String>(), neighbours.map { it.text }.toSet())

    val nameToReplace = "startIndexing"
    val startOffset = myFixture.editor.document.text.indexOf(nameToReplace)
    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.editor.document.replaceString(startOffset, startOffset + nameToReplace.length, "buyHelicopter")
      myFixture.editor.document.saveToDisk() // This is how we trigger reindexing
    }

    TimeoutUtil.sleep(2000) // wait for two seconds for index update

    assertEquals(5, storage.index.size)

    neighbours = storage.streamSearchNeighbours("begin indexing", 0.5).toList()
    assertEquals(setOf("ProjectIndexingTask"), neighbours.map { it.text }.toSet())

    neighbours = storage.streamSearchNeighbours("helicopter purchase", 0.5).toList()
    assertEquals(setOf("buyHelicopter"), neighbours.map { it.text }.toSet())
  }

  fun `test removal of file with method changes the index`() = runTest {
    setupTest("java/ProjectIndexingTask.java", "kotlin/ScoresFileManager.kt")
    assertEquals(5, storage.index.size)

    var neighbours = storage.streamSearchNeighbours("begin indexing", 0.5).toList()
    assertEquals(setOf("startIndexing", "ProjectIndexingTask"), neighbours.map { it.text }.toSet())

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).toList()
    assertEquals(setOf("handleScoresFile", "clearFileWithScores"), neighbours.map { it.text }.toSet())

    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.editor.virtualFile.deleteRecursively() // deletes the currently open file: java/IndexProjectAction.java
    }

    TimeoutUtil.sleep(2000) // wait for two seconds for index update

    assertEquals(2, storage.index.size)

    neighbours = storage.streamSearchNeighbours("begin indexing", 0.5).toList()
    assertEquals(emptySet<String>(), neighbours.map { it.text }.toSet())

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).toList()
    assertEquals(setOf("handleScoresFile", "clearFileWithScores"), neighbours.map { it.text }.toSet())
  }

  private suspend fun setupTest(vararg filePaths: String) {
    SemanticSearchFileContentListener.getInstance(project).clearEvents()
    myFixture.configureByFiles(*filePaths)
    LocalArtifactsManager.getInstance().downloadArtifactsIfNecessary()
    SemanticSearchSettings.getInstance().enabledInSymbolsTab = true
    storage.generateEmbeddingsIfNecessary().join()
  }
}